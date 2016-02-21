package mx.domain.repo.local

import mx.domain.repo.{HasDB, Schema, Folder}

trait FolderRepo { self: Schema with HasDB ⇒
  import driver.simple._
  
  def createFolder(userId: Long, name: String): Unit = db.withSession { implicit session: Session ⇒
    Folders.insert(Folder(None, userId, name))
  }
  
  def getFolderByName(userId: Long, name: String): Option[Folder] = db.withSession { implicit session: Session ⇒
    Query(Folders).where(folder ⇒ folder.userId === userId && folder.name === name).firstOption
  }

  def getFolders(userId: Long): List[(Long, String)] = db.withSession { implicit session: Session ⇒
    val query = for {
      folder <- Folders if folder.userId === userId
    } yield (folder.id, folder.name)
    
    query.list
  }
  
  def getFolderListingForUser(userId: Long, folderId: Long) = db.withSession { implicit session: Session ⇒
    val query = for {
      mail <- FolderMails if mail.userId === userId && mail.folderId === folderId
    } yield (mail.id, mail.from, mail.fromAddr, mail.subject, mail.attachments, mail.sent, mail.flgSeen)

    query.sortBy(_._6.desc).list
  }
}
