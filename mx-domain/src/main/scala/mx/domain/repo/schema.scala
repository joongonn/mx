package mx.domain.repo

import java.sql.Timestamp

import scala.slick.driver.ExtendedProfile
import scala.slick.lifted.ColumnOption.DBType
import scala.slick.session.Session

import mx.domain.repo.delivery._
import mx.domain.repo.local._

object MailQStatus extends Enumeration {
  type Status = Value
  val New, Deliverying, Delivered, Undeliverable = Value
}

case class MailQ(id: Option[Long], recipient: String, domain: String, delivered: Boolean, attempts: Int, lastAttempt: Option[Timestamp], status: MailQStatus.Status, mailDataId: Long)
case class MailData(id: Option[Long], returnPath: String, rfc822: String)

case class User(id: Option[Long], email: String, passwdHash: String)
case class Folder(id: Option[Long], userId: Long, name: String)
case class MailFlags(seen: Boolean, answered: Boolean, flagged: Boolean, deleted: Boolean, draft: Boolean)
case class FolderMail(id: Option[Long], userId: Long, folderId: Long, from: Option[String], fromAddr: String, subject: String, attachments: Int, sent: Timestamp, flags: MailFlags, mailDataId: Long)

trait Schema {
  val driver: ExtendedProfile
  
  import driver.simple._

  val Tables = Seq(MailDatas, MailQs, Users, Folders, FolderMails)
  
  implicit val statusTypeMapper = MappedTypeMapper.base[MailQStatus.Status, String](
      { status ⇒ status.toString },
      { status ⇒ MailQStatus.withName(status) }
  )
    
  object MailDatas extends Table[MailData]("mail_data") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def returnPath = column[String]("return_path")
    def data = column[String]("data", DBType("CLOB"))
    
    def * = id.? ~ returnPath ~ data <> (MailData, MailData.unapply _)
    def autoinc = * returning id
  }

  object MailQs extends Table[MailQ]("mail_q") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def recipient = column[String]("recipient")
    def domain = column[String]("domain")
    def delivered = column[Boolean]("delivered", O.Default(false))
    def attempts = column[Int]("attempts", O.Default(0))
    def lastAttempt = column[Option[Timestamp]]("last_attempt")
    def status = column[MailQStatus.Status]("status")
    
    def mailDataId = column[Long]("mail_data_id")
    
    def mailData = foreignKey("mail_q_mail_data_fk", mailDataId, MailDatas)(_.id)
    
    def * = id.? ~ recipient ~ domain ~ delivered ~ attempts ~ lastAttempt ~ status ~ mailDataId <> (MailQ, MailQ.unapply _)
    def autoinc = * returning id
  }
  
  object Users extends Table[User]("user") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def password = column[String]("password")
    
    def emailIndex = index("email_idx", email)
    
    def * = id.? ~ email ~ password <> (User, User.unapply _)
  }
  
  object Folders extends Table[Folder]("folder") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def name = column[String]("name")
    
    def user = foreignKey("folders_user_fk", userId, Users)(_.id)
    
    def userIdIndex = index("user_id_idx", userId)
    
    def * = id.? ~ userId ~ name <> (Folder, Folder.unapply _)
  }
  
  object FolderMails extends Table[FolderMail]("mail") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def userId = column[Long]("user_id")
    def folderId = column[Long]("folder_id")
    def from = column[Option[String]]("from")
    def fromAddr = column[String]("from_addr")
    def subject = column[String]("subject")
    def data = column[String]("data", DBType("CLOB")) //FIXME: clob does not work with mysql
    def attachments = column[Int]("attachments")
    def sent = column[Timestamp]("sent")
    
    def flgSeen = column[Boolean]("flg_seen", O.Default(false))
    def flgAnswered = column[Boolean]("flg_answered", O.Default(false))
    def flgFlagged = column[Boolean]("flg_flagged", O.Default(false))
    def flgDeleted = column[Boolean]("flg_deleted", O.Default(false))
    def flgDraft = column[Boolean]("flg_draft", O.Default(false))
    
    def mailDataId = column[Long]("mail_data_id")
    
    def user = foreignKey("mail_user_fk", userId, Users)(_.id)
    def folder = foreignKey("mail_folder_fk", folderId, Folders)(_.id)
    def mailData = foreignKey("mail_mail_data_fk", mailDataId, MailDatas)(_.id)
    
    def pack(id: Option[Long], userId: Long, folderId: Long, from: Option[String], fromAddr: String, subject: String, attachments: Int, sent: Timestamp, flgSeen: Boolean, flgAnswered: Boolean, flgFlagged: Boolean, flgDeleted: Boolean, flgDraft: Boolean, mailDataId: Long): FolderMail = {
        FolderMail(id, userId, folderId, from, fromAddr, subject, attachments, sent, MailFlags(flgSeen, flgAnswered, flgFlagged, flgDeleted, flgDraft), mailDataId)
    }
    
    def unpack(mail: FolderMail) = {
      val FolderMail(id, userId, folderId, from, fromAddr, subject, attachements, sent, MailFlags(flgSeen, flgAnswered, flgFlagged, flgDeleted, flgDraft), mailDataId) = mail
      Some(id, userId, folderId, from, fromAddr, subject, attachements, sent, flgSeen, flgAnswered, flgFlagged, flgDeleted, flgDraft, mailDataId)
    }
    
    def * = id.? ~ userId ~ folderId ~ from ~ fromAddr ~ subject ~ attachments ~ sent ~ flgSeen ~ flgAnswered ~ flgFlagged ~ flgDeleted ~ flgDraft ~ mailDataId <> (pack _ , unpack _)
  }
}
