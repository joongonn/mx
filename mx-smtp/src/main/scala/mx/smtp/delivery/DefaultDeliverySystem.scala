package mx.smtp.delivery

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import org.slf4j.LoggerFactory

import mx.domain.repo.JdbcMxRepository

case object RecipientNotLocal extends CheckFail

class DefaultDeliverySystem(val repo: JdbcMxRepository)(implicit val executionCtx: ExecutionContext) extends DeliverySystem with SenderChecking with RecipientChecking with EnvelopeChecking {
  import mx.smtp.inbound.Envelope
  
  def checkSender(from: String)(timeout: Duration)(onSenderCheckComplete: CheckResult ⇒ Unit) = executeCheck(timeout) {
    if (true) {
      Right() 
    } else {
      Left(Rejected)
    }
  }(onSenderCheckComplete)
  
  def checkRecipient(toAddr: String)(timeout: Duration)(onRecipientCheckComplete: CheckResult ⇒ Unit) = executeCheck(timeout) {
    if (repo.userEmailExists(toAddr)) {
      Right()
    } else {
      Left(RecipientNotLocal)
    }
  }(onRecipientCheckComplete)
  
  def checkEnvelope(envelope: Envelope)(timeout: Duration)(onEnvelopeCheckComplete: CheckResult ⇒ Unit) = executeCheck(timeout) {
    if (true) {
      Right()
    } else {
      Left(Rejected)
    }
  }(onEnvelopeCheckComplete)
  
  def queueForDelivery(envelope: Envelope) = {
    import mx.domain.mime._
    
    //TODO: local vs relay
    val data = envelope.mailData.toString
    val email = toEmail(data)
    for {
      toAddress <- envelope.recipients
      userId <- repo.getUserByEmail(toAddress).get.id
      inbox <- repo.getFolderByName(userId, "INBOX")
      mailDataId <- repo.insertMailData(envelope.returnPath.get, data).id
    } repo.insertMail(userId, inbox.id.get, email.from.personal, email.from.address, email.subject, email.attachments.length, email.sent.getOrElse(new java.util.Date).getTime, mailDataId)
  }
}
