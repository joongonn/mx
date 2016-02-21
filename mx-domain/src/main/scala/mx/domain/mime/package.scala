package mx.domain

import java.io.{ByteArrayInputStream, FileInputStream}
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.Date

import javax.mail.{Address ⇒ JavaMailAddress, BodyPart, Session}
import javax.mail.internet.{InternetAddress, MimeBodyPart, MimeMessage, MimeMultipart}
import javax.mail.Message.RecipientType

import scala.util.matching.Regex

package object mime {

  object MailFmts {
    val Text = "text"
    val Html = "html"
    val Original = "original"

    val * = Text :: Html :: Original :: Nil
  }

  def toEmailAddress(address: JavaMailAddress) = address match {
      case addr: InternetAddress ⇒ EmailAddress(addr.getAddress, Option(addr.getPersonal))
      case _ @ from ⇒ EmailAddress(from.toString, None)
  }
  
  def toEmail(rfc822: String) = {
    import MailFmts._
    import javax.mail.Message.RecipientType

    val TextPlain = "^text/plain.*".r
    val TextHtml = "^text/html.*".r
    val MultipartAlternative = "^multipart/alternative.*".r
    
    val m = new MimeMessage(null, new ByteArrayInputStream(rfc822.getBytes))
    val subject = m.getSubject
    val from = toEmailAddress(m.getFrom()(0)) 
    val to = Option(m.getRecipients(RecipientType.TO)).flatMap { addrs ⇒ Some(addrs.map(toEmailAddress).toList) }
    val cc = Option(m.getRecipients(RecipientType.CC)).flatMap { addrs ⇒ Some(addrs.map(toEmailAddress).toList) }
    val sent = Option(m.getSentDate)
    
    val availableFmts = collection.mutable.Map.empty[String, String]
    val attachments = collection.mutable.MutableList.empty[EmailAttachment]
    
    def findFirst(mp: BodyPart, contentType: Regex): Option[BodyPart] = {
      if (mp.getContentType.matches(contentType.toString)) {
        Some(mp)
      } else {
        mp.getContent match {
          case mprt: MimeMultipart ⇒
            val parts = (0 to (mprt.getCount -1)).map { mprt.getBodyPart(_) }
            val found = parts.find {
              findFirst(_, contentType).isDefined
            }
            found
          case _ ⇒ None
        }
      }
    }
    
    m.getContent match {
      case mprt: MimeMultipart ⇒
        (0 to (mprt.getCount - 1)) foreach { i ⇒
          mprt.getBodyPart(i) match {
            case bp: MimeBodyPart ⇒
              bp.getContentType match {
                case TextPlain() ⇒
                  availableFmts += (Text -> bp.getContent.asInstanceOf[String])
                case TextHtml() ⇒ 
                  availableFmts += (Html -> bp.getContent.asInstanceOf[String])
                case MultipartAlternative() ⇒
                  findFirst(bp, TextPlain) foreach { k ⇒ availableFmts += (Text -> k.getContent.asInstanceOf[String])}
                  findFirst(bp, TextHtml) foreach { k ⇒ availableFmts += (Html -> k.getContent.asInstanceOf[String])}
                case x ⇒ //if "attachment".equalsIgnoreCase(bp.getDisposition)
                  attachments += EmailAttachment(x, bp.getFileName, bp.getSize)
              }
          }
        }
      case s : String ⇒
        availableFmts += (Text -> s)
    }
    
    availableFmts += (Original -> rfc822)
    
    Email(subject, from, to, availableFmts.toMap, attachments.to[List], sent)
  }
  
  def toPlainTextMimeMessage(from: String, to: Option[Array[String]], cc: Option[Array[String]], bcc: Option[Array[String]], subject: String, body: String) = {
    import RecipientType._
    
    val fromAddr = new InternetAddress(from)
    val msg = new MimeMessage(null: Session)
    msg.addFrom(Array(fromAddr))
    to.foreach { _.foreach { recipient ⇒ msg.addRecipients(RecipientType.TO, recipient) } }
    cc.foreach { _.foreach { recipient ⇒ msg.addRecipients(RecipientType.CC, recipient) } }
    bcc.foreach { _.foreach{ recipient ⇒ msg.addRecipients(RecipientType.BCC, recipient) } }
    msg.setSubject(subject)
    msg.setContent(body, "text/plain")

    val os = new java.io.ByteArrayOutputStream
    msg.writeTo(os)
    os.toString
  }
}
