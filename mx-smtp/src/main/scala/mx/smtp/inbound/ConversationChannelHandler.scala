package mx.smtp.inbound

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Success, Failure}

import org.slf4j.LoggerFactory

import io.netty.channel.{ChannelFuture, ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.ssl.SslHandler

import mx.common.net.Netty._
import mx.common.net.SslEngineProvider
import mx.common.ConnectionSession
import mx.smtp.delivery._

class ConversationChannelHandler(sslEngineProvider: SslEngineProvider, deliverySystem: DefaultDeliverySystem) extends SimpleChannelInboundHandler[java.lang.String] {
  import Conversation.States._
  import Conversation.Replies._
  
  implicit private val logger = LoggerFactory.getLogger(classOf[ConversationChannelHandler])

  private var ctx: ChannelHandlerContext = null
  
  @volatile private var state = AwaitGreeting
  private val session = ConnectionSession()
  private var envelope = Envelope()
  
  override def channelActive(ctx: ChannelHandlerContext) = {
    session.info("Connected from [{}]", ctx.channel.remoteAddress)
    this.ctx = ctx
    reply(Banner)
  }

  override def channelInactive(ctx: ChannelHandlerContext) = {
    session.info("Connection [{}] closed", ctx.channel.remoteAddress)
  }
  
  override def channelRead0(ctx: ChannelHandlerContext, line: java.lang.String) = state match {
    case AwaitDeliverySystemReply ⇒ session.debug("Awaiting DeliverySystem reply, discarding input:[{}]", line)
    case AwaitData ⇒ receiveData(line)
    case _ ⇒ receiveCommand(line)
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    cause match {
      case _ : io.netty.handler.timeout.TimeoutException ⇒
        session.warn("Idle timeout")
      case _ ⇒
        session.error("Fatal pipeline exception caught:[{}] - {}, closing connection", cause.getClass.getName, cause.getMessage)
    }
    
    ctx.close()
  }
  
  private def reply(line: String): Option[ChannelFuture] = {
    if (ctx.channel.isActive) {
      session.debug("Sending reply:[{}]", line)
      Some(ctx.writeAndFlush(line + "\r\n"))
    } else {
      session.debug("Channel already closed, discarding reply:[{}]", line)
      None
    }
  }
  
  private def receiveCommand(line: String) = {
    import Conversation.CommandMatchers._
    session.debug("Received command: [{}]", line)
    line match {
      case EhloCmd(who) ⇒ respondToEhlo(Option(who))
      case HeloCmd(who) ⇒ respondToHelo(Option(who))
      case StartTlsCmd() ⇒ respondToStartTls()
      case MailCmd(returnPath) ⇒ respondToMailFrom(Option(returnPath))
      case RcptCmd(to) ⇒ respondToRcpt(Option(to))
      case DataCmd() ⇒ respondToData()
      case RsetCmd() ⇒ respondToRset()
      case QuitCmd() ⇒ respondToQuit()
      case NoopCmd() ⇒ reply(NotImplemented)
      case HelpCmd() ⇒ reply(NotImplemented)
      case _ ⇒ reply(Unrecognized)
    }
  }
  
  private def receiveData(line: String) = {
    val EndOfData = "."
    val DotStuffed = """\.(.+)""".r
    
    session.trace("Received data: [{}]", line)
    line match {
      case EndOfData ⇒
        session.info("Received [{}] bytes of mail body data", envelope.size())
        callCheck(deliverySystem.checkEnvelope(envelope)(5 seconds)_) {
          case Accepted ⇒
            reply(DataQueuedForDelivery)
            state = AwaitSenderAddr
            deliverySystem.queueForDelivery(envelope)
            envelope = Envelope()
          case reason: CheckFail ⇒
            reply(DataFail(reason))
            state = AwaitSenderAddr
        }
      case DotStuffed(unstuffed) ⇒ envelope.mailData.append(unstuffed).append("\r\n")
      case _ ⇒ envelope.mailData.append(line).append("\r\n")
    }
  }
  
  private def respondToStartTls(): Unit = if (session.tlsStarted) {
    reply(TlsAlreadyStarted)
  } else {
    session.debug("Adding SslHandler to channel pipeline")
    ctx.pipeline().addFirst(new SslHandler(sslEngineProvider.createServerEngine(), true))
    session.tlsStarted = true
    reply(ReadyToStartTls)
  }
  
  private def respondToHelo(domain: Option[String]): Unit = state match {
    case _ ⇒ domain match {
      case Some(domain) if !domain.isEmpty ⇒
        envelope.clear()
        envelope.domain = Some(domain)
        state = AwaitSenderAddr
        reply(HeloOk(domain))
      case _ ⇒ reply(HeloSyntax)
    }
  }
  
  private def respondToEhlo(domain: Option[String]): Unit = state match {
    case _ ⇒ domain match {
      case Some(domain) if !domain.isEmpty ⇒
        envelope.clear()
        envelope.domain = Some(domain)
        state = AwaitSenderAddr
        EhloOk.foreach { reply(_) }
      case _ ⇒ reply(EhloSyntax)
    }
  }
  
  private def respondToMailFrom(returnPath: Option[String]): Unit = state match {
    case AwaitSenderAddr | AwaitRecipients ⇒ returnPath match {
      case Some(returnPath) if !returnPath.isEmpty ⇒
        session.debug("Checking sender address:[{}] ...", returnPath)
        callCheck(deliverySystem.checkSender(returnPath)(5 seconds)_) {
          case Accepted ⇒
            session.debug("Return-path [{}] accepted", returnPath)
            envelope.returnPath = Some(returnPath)
            state = AwaitRecipients
            reply(MailOk)
          case reason: CheckFail ⇒
            session.info("Return-path [{}] rejected, reason:[{}]", returnPath, reason)
            reply(MailFail(reason))
        }
      case _ ⇒ reply(MailSyntax)
    }
    case AwaitGreeting ⇒ reply(HeloFirst)
  }
  
  private def respondToRcpt(to: Option[String]): Unit = state match {
    case AwaitRecipients ⇒ to match {
      case Some(to) if !to.isEmpty ⇒
        session.debug("Checking recipient address:[{}] ...", to)
        callCheck(deliverySystem.checkRecipient(to)(5 seconds)_) {
          case Accepted ⇒
            envelope.recipients += to
            session.debug("Recipient [{}] accepted (total: {})", to, envelope.recipients.length)
            reply(RcptOk)
          case reason: CheckFail ⇒
            session.info("Recipient [{}] rejected, reason:[{}]", to, reason)
            reply(RcptFail(reason))
        }
      case _ ⇒ reply(RcptSyntax)
    }
    case AwaitGreeting ⇒ reply(HeloFirst)
    case AwaitSenderAddr ⇒ reply(MailFirst)
  }
  
  private def respondToData(): Unit = state match {
    case AwaitRecipients ⇒ if (envelope.recipients.isEmpty) {
      reply(RcptFirst)
    } else {
      state = AwaitData
      reply(DataOk)
    }
    case AwaitGreeting ⇒ reply(HeloFirst)
    case AwaitSenderAddr ⇒ reply(MailFirst)
  }
  
  private def respondToQuit():Unit = {
    reply(Bye).foreach(_.onSuccess{ _ ⇒
      ctx.close()
    })
  }

  private def respondToRset(): Unit = {
    envelope.clear()
    state = AwaitGreeting
  }
  
  // Wait for check result, ignoring any input from the client (ie. no pipelining)
  private def callCheck(checkMethod: (CheckResult ⇒ Unit) ⇒ Unit)(onCheckComplete: PartialFunction[CheckResult, Unit]) = {
    val prevState = state
    state = AwaitDeliverySystemReply
    val restoreState: PartialFunction[CheckResult, CheckResult] = {
      case result: CheckResult ⇒
        state = prevState
        result
    }
    checkMethod(restoreState andThen (onCheckComplete orElse {
      case Error(t) ⇒
        session.error("Check error:[{}] - {}", t.getClass, t.getMessage)
        reply(TransactionFailed)
    }))
  }
}
