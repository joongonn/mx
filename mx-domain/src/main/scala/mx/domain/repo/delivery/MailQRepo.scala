package mx.domain.repo.delivery

import mx.domain.repo.{HasDB, MailData, MailQ, Schema}
import mx.domain.repo.MailQStatus

trait MailQRepo { self: Schema with HasDB ⇒
  import driver.simple._

  def insertMailQ(recipient: String, domain: String, mailDataId: Long): MailQ = db.withSession { implicit session: Session ⇒
    val q = MailQ(None, recipient, domain, false, 0, None, MailQStatus.New, mailDataId)
    val insertedId = MailQs.autoinc.insert(q)
    q.copy(id = Some(insertedId))
  }

  def getUndeliveredQItem() = db.withSession { implicit session: Session ⇒
    val query = for {
      mailQ <- MailQs if mailQ.delivered === false && mailQ.status =!= MailQStatus.Deliverying
    } yield mailQ
    
    query.take(1).firstOption
  }
  
  def checkoutMailQItem(id: Long) = db.withSession { implicit session: Session ⇒
    val checkout = for { q <- MailQs if q.id === id && q.delivered === false && q.status =!= MailQStatus.Deliverying } yield q.status
    checkout.update(MailQStatus.Deliverying) > 0
  }
  
  def updateMailQItemStatus(id: Long, delivered: Boolean, status: MailQStatus.Status) = db.withSession { implicit session: Session ⇒
    val u = for { q <- MailQs if q.id === id } yield q.delivered ~ q.status
    u.update((delivered, status)) > 0
  }
}