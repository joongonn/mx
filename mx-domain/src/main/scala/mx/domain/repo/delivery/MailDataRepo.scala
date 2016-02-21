package mx.domain.repo.delivery

import mx.domain.repo.{HasDB, MailData, Schema}

trait MailDataRepo { self: Schema with HasDB ⇒
  import driver.simple._
  
  def insertMailData(returnPath: String, rfc822: String): MailData = db.withSession { implicit session: Session ⇒
    val mailData = MailData(None, returnPath, rfc822)
    val insertedId = MailDatas.autoinc.insert(mailData)
    MailData(Some(insertedId), returnPath, rfc822)
  }

  def getMailDataById(id: Long): Option[MailData] = db.withSession { implicit session: Session ⇒ 
    Query(MailDatas).where(m ⇒ m.id === id).firstOption
  }
}
