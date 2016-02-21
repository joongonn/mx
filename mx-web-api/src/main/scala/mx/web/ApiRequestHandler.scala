package mx.web

import java.util.Date

import com.fasterxml.jackson.databind.annotation.JsonDeserialize // https://github.com/FasterXML/jackson-module-scala/issues/106

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultFullHttpRequest, DefaultFullHttpResponse, HttpMethod, HttpRequest, HttpResponse}
import io.netty.handler.codec.http.HttpResponseStatus._

import org.slf4j.LoggerFactory

import mx.common.Logging._
import mx.domain._
import mx.domain.mime._
import mx.domain.repo.JdbcMxRepository
import mx.domain.repo.MailFlags

import AuthTokens._

case class FolderEntry(id: Long, name: String)
case class FolderMailEntry(mailId: Long, from: EmailAddress, subject: String, attachments: Int, sent: Long, flgSeen: Boolean)
case class ReadMail(id: Long, folderId: Long, from: EmailAddress, to: Option[List[EmailAddress]], subject: String, data: String, sent: Date, flags: MailFlags, fmt: String, availableFmts: List[String], attachments: List[EmailAttachment])

case class AuthReq(email: String, password: String)
case class MailUpdateReq(@JsonDeserialize(contentAs = classOf[java.lang.Long]) mailId: Option[Long], folder: Option[String], flgSeen: Option[Boolean], flgDeleted:Option[Boolean])
case class ComposeReq(subject: Option[String], to: Option[Array[String]], cc: Option[Array[String]], bcc: Option[Array[String]], data: Option[String])

case class ApiError(status: Int, description: String)

object ApiRequestHandler {
  import io.netty.handler.codec.http.HttpVersion._
  import io.netty.handler.codec.http.HttpHeaders._
  import io.netty.handler.codec.http.HttpHeaders.Names._
  import HandlerUtils.ResponseWrapper

  private val AuthTokenHeader = "X-MX-AuthToken"
  private val AuthTokenTTLMs = 8 * 60 * 60 * 1000
  
  object Routes {
    val Auth              = """^/auth/?$""".r
    val ValidateAuthToken = """^/auth/(.*)$""".r
    val GetFolders        = """^/folder/?$""".r
    val GetFolderMails    = """^/folder/(.*)$""".r
    val Mail              = """^/mail/?$""".r
    val GetMail           = """^/mail/(\d+)(?:\?(.*))?$""".r
    val MaildId           = """^/mail/(\d+)$""".r
  }
  
  case class Request(method: HttpMethod, uri: String, authToken: Option[Either[TokenError, AuthToken]])

  private def notFound() = {
    val resp = new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND)
    resp.applyHeaders(CONTENT_LENGTH -> 0)
  }
  
  private def okEmpty() = new DefaultFullHttpResponse(HTTP_1_1, NO_CONTENT)

  private def unauthorized() = new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED)

  private def forbidden() = new DefaultFullHttpResponse(HTTP_1_1, FORBIDDEN)
  
  private def internalServerError() = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR);
}

@ChannelHandler.Sharable
class ApiRequestHandler(val repo: JdbcMxRepository) extends SimpleChannelInboundHandler[DefaultFullHttpRequest] {
  import ApiRequestHandler._
  import HandlerUtils._
  import HttpMethod._
  import Routes._
  import mx.common.net.Netty._

  private val logger = LoggerFactory.getLogger(classOf[ApiRequestHandler])
  private val authTokens = AuthTokens()
  
  override def channelRead0(ctx: ChannelHandlerContext, httpReq: DefaultFullHttpRequest) = {
    val started = System.currentTimeMillis
    
    val tokenHeader = httpReq.headers.get(AuthTokenHeader) 
    val authToken = if (tokenHeader == null) None else Some(authTokens.crack(tokenHeader))
    val request = Request(httpReq.getMethod, httpReq.getUri, authToken)

    val response = request match { 
      case Request(OPTIONS, _, _) ⇒ okEmpty()
      case Request(POST, Auth(), _) ⇒ authenticate(httpReq.to[AuthReq])
      case Request(GET, ValidateAuthToken(authToken), _) ⇒ validateAuthToken(authToken)

      case Request(_, _, Some(Left(_))) ⇒ unauthorized()
      
      case Request(GET, GetFolders(), Some(Right(authToken))) ⇒ getFolders()(authToken)
      case Request(GET, GetFolderMails(folderName), Some(Right(authToken))) ⇒ getMailsInFolder(folderName)(authToken)
      case Request(GET, GetMail(id, queryString), Some(Right(authToken))) ⇒ readMailById(id.toLong, toQueryMap(Option(queryString)))
      
      case Request(POST, Mail(), Some(Right(authToken))) ⇒ sendMail(httpReq.to[ComposeReq])(authToken)
        
      case Request(PUT, MaildId(id), Some(Right(authToken))) ⇒ updateMailById(id.toLong, httpReq.to[MailUpdateReq])(authToken)
      case Request(PUT, Mail(), Some(Right(authToken))) ⇒ updateMails(httpReq.to[Array[MailUpdateReq]])(authToken)
        
      case Request(DELETE, MaildId(id), Some(Right(authToken))) ⇒ deleteMailById(id.toLong)
      case Request(DELETE, Mail(), Some(Right(authToken))) ⇒ deleteMails(httpReq.to[Array[Long]])
      
      case _ ⇒ notFound()
    }
    
    ctx.writeAndFlush(response.applyCorsHeaders) onComplete { channel ⇒
      val timeTaken = System.currentTimeMillis - started
      logger.debug("{} [{}] {}ms HTTP {}", Seq(request.method, request.uri, timeTaken, response.getStatus.code).toLoggerArgs)
      channel.close()
    }
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    logger.error("Exception caught, closing channel:", cause)
    ctx.writeAndFlush {
      ApiError(INTERNAL_SERVER_ERROR.code, cause.getMessage).toJsonResponse(INTERNAL_SERVER_ERROR).applyCorsHeaders
    } addListener(ChannelFutureListener.CLOSE)
  } 
  
  private def authenticate(req: AuthReq) = {
    import org.mindrot.jbcrypt.BCrypt

    logger.trace("Authenticating [{}]", req.email)

    val (email, password) = (req.email, req.password)
     
    val authToken = for {
      user <- repo.getUserByEmail(email)
      userId <- user.id if (BCrypt.checkpw(password, user.passwdHash)) 
    } yield {
      val expireAt = System.currentTimeMillis + AuthTokenTTLMs
      AuthToken(authTokens.generate(userId, email, expireAt), userId, email, expireAt)
    }
    
    authToken match {
      case Some(authToken) ⇒ authToken.toJsonResponse
      case _ ⇒ unauthorized()
    }
  }
  
  private def getFolders()(implicit authToken: AuthToken) = {
    val resp = repo.getFolders(authToken.userId).map { entry ⇒ FolderEntry(entry._1, entry._2) }
    resp.toJsonResponse
  }
  
  private def readMailById(id: Long, queryMap: QueryMap) = {
    import MailFmts._

    repo.getMailByIdWithData(id) match {
      case Some((mail, mailData)) ⇒
        val email = toEmail(mailData.rfc822)
        val returnFormat = queryMap.get("fmt") match {
          case None | Some(None) ⇒ Text
          case Some(Some(fmt)) ⇒ fmt
        }
        val availableFmts = email.availbleFmts
        ReadMail(mail.id.get,
            mail.folderId,
            EmailAddress(mail.fromAddr, mail.from),
            email.to,
            mail.subject,
            availableFmts.get(returnFormat).get,
            mail.sent,
            mail.flags,
            returnFormat,
            availableFmts.keys.filterNot(_ == returnFormat).toList,
            email.attachments).toJsonResponse // .applyCacheHeaders(mail.sent.getTime, 300)
      case None ⇒
        notFound()
    }
  }
  
  private def updateMailById(id: Long, update: MailUpdateReq)(implicit authToken: AuthToken) = {
    val newFolderId = update.folder.flatMap { folderName ⇒ repo.getFolderByName(authToken.userId, folderName).get.id }
    repo.updateMail(id.toLong, newFolderId, update.flgSeen, None, None, update.flgDeleted, None)
    okEmpty()
  }
  
  private def updateMails(updates: Array[MailUpdateReq])(implicit authToken: AuthToken) = {
    updates.foreach { u ⇒ 
      //FIXME: folderName resolution & update batching
      val newFolderId = u.folder.flatMap { folderName ⇒ repo.getFolderByName(authToken.userId, folderName).get.id }
      repo.updateMail(u.mailId.get, newFolderId, u.flgSeen, None, None, u.flgDeleted, None)
    }
    okEmpty()
  }
  
  private def deleteMailById(id: Long) = {
    repo.deleteMailById(id)
    okEmpty()
  }
  
  private def deleteMails(ids: Array[Long]) = {
    ids.foreach(repo.deleteMailById(_))
    okEmpty()
  }
  
  private def getMailsInFolder(name: String)(implicit authToken: AuthToken) = {
    val folder = repo.getFolderByName(authToken.userId, name).get
    val listing = repo.getFolderListingForUser(authToken.userId, folder.id.get).map { entry ⇒ FolderMailEntry(entry._1, EmailAddress(entry._3, entry._2), entry._4, entry._5, entry._6.getTime, entry._7) }
    listing.toJsonResponse
  }
  
  private def validateAuthToken(token: String) = {
    logger.debug("Validating token:[{}]", token)
    authTokens.crack(token) match {
      case Right(authToken) ⇒ authToken.toJsonResponse
      case _ ⇒ unauthorized()
    }
  }
  
  //FIXME: transacted/atomic operation
  private def sendMail(req: ComposeReq)(implicit authToken: AuthToken) = {
    val DomainPart = """(^.*?@(.*))$""".r
    
    def qForDelivery(mailDataId: Long)(recipients: Array[String]) = recipients.foreach { case DomainPart(email, domainPart) ⇒ repo.insertMailQ(email, domainPart, mailDataId) }  
    
    val rfc822Data = toPlainTextMimeMessage(authToken.email, req.to, req.cc, req.bcc, req.subject.get, req.data.get)
    val mailData = repo.insertMailData(authToken.email, rfc822Data)
    val sentFolder = repo.getFolderByName(authToken.userId, "SENT")
    repo.insertMail(authToken.userId, sentFolder.get.id.get, None, authToken.email, req.subject.get, -1, System.currentTimeMillis, mailData.id.get)
    
    val mailDataId = mailData.id.get
    req.to.foreach { qForDelivery(mailDataId)_ } 
    req.cc.foreach { qForDelivery(mailDataId)_ }
    req.bcc.foreach { qForDelivery(mailDataId)_ }
    
    okEmpty()
  }
}
