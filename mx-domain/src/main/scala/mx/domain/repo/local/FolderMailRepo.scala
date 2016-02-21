package mx.domain.repo.local

import java.sql.Timestamp

import mx.domain.repo.{HasDB, Schema}
import mx.domain.repo.{FolderMail, MailData, MailFlags}

trait FolderMailRepo { self: Schema with HasDB ⇒
  import driver.simple._
  
  def insertMail(userId: Long, folderId: Long, from: Option[String], fromAddr: String, subject: String, attachments: Int, sent: Long, mailDataId: Long) = db.withSession { implicit session: Session ⇒
    FolderMails.insert(FolderMail(None, userId, folderId, from, fromAddr, subject, attachments, new Timestamp(sent), MailFlags(false, false, false, false, false), mailDataId))
  }
  
  def getMailById(id: Long): Option[FolderMail] = db.withSession { implicit session: Session ⇒
    val mail = Query(FolderMails).where(m ⇒ m.id === id).firstOption
    mail
  }
  
  def getMailByIdWithData(id: Long): Option[(FolderMail, MailData)] = db.withSession { implicit session: Session ⇒
    val query = for {
      mail <- FolderMails if mail.id === id
      mailData <- MailDatas if mailData.id === mail.mailDataId
    } yield (mail, mailData)

    val updateSeen = for { m <- FolderMails if m.id === id } yield m.flgSeen //TODO: move out
    updateSeen.update(true)
    
    query.firstOption
  }
  
  def getMailsForUser(userId: Long): List[FolderMail] = db.withSession { implicit session: Session ⇒
    Query(FolderMails).where(m ⇒ m.userId === userId).list()
  }
  
  def getMailsInFolder(folderId: Long): List[FolderMail] = db.withSession { implicit session: Session ⇒
    Query(FolderMails).where(m ⇒ m.folderId === folderId).list()
  }
  
  def updateMail(id: Long, folderId: Option[Long], flgSeen: Option[Boolean], flgAnswered: Option[Boolean], flgFlagged: Option[Boolean], flgDeleted: Option[Boolean], flgDraft: Option[Boolean]): Unit = db.withSession { implicit session: Session ⇒
    val mail = getMailById(id).get
    val updated = mail.copy( //TODO: lenses
        folderId = folderId.getOrElse(mail.folderId),
        flags = mail.flags.copy(
            seen = flgSeen.getOrElse(mail.flags.seen),
            answered = flgAnswered.getOrElse(mail.flags.answered),
            flagged = flgFlagged.getOrElse(mail.flags.flagged),
            deleted = flgDeleted.getOrElse(mail.flags.deleted),
            draft = flgDraft.getOrElse(mail.flags.draft)))
            
    val q = for { m <- FolderMails if m.id === id } yield m
    q.update(updated)
  }
  
  def deleteMailById(id: Long) = db.withSession { implicit session: Session ⇒
    val mail = Query(FolderMails).where(m ⇒ m.id === id)
    mail.delete > 0
  }
}
