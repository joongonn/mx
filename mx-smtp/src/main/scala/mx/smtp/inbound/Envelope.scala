package mx.smtp.inbound

import java.io.ByteArrayInputStream

import javax.mail.internet.MimeMessage

import scala.collection.mutable.MutableList

object Envelope {
  def apply() = new Envelope(None, None, new MutableList[String](), new StringBuilder)
}

case class Envelope(var domain: Option[String], var returnPath: Option[String], val recipients: MutableList[String], val mailData: StringBuilder) {
  def size() = mailData.length
  
  def clear() = {
    domain = None
    returnPath = None
    recipients.clear()
    mailData.clear()
  }
}
