package mx.domain.repo.local

import org.mindrot.jbcrypt.BCrypt

import mx.domain.repo.{HasDB, Schema, User}

trait UserRepo { self: Schema with HasDB ⇒
  import driver.simple._
  
  def createUser(email: String, password: String): Unit = db.withSession { implicit session: Session ⇒
    val newUser = User(None, email, BCrypt.hashpw(password, BCrypt.gensalt()))
    Users.insert(newUser)
  }
  
  def userEmailExists(email: String): Boolean = db.withSession { implicit session: Session ⇒
    Query(Users.where(u ⇒ u.email === email).exists).first
  }
  
  def getUserByEmail(email: String): Option[User] = db.withSession { implicit session: Session ⇒
    Query(Users).where(u ⇒ u.email === email).firstOption
  }
  
  def getUserByEmailAndPassword(email: String, password: String): Option[User] = db.withSession { implicit session: Session ⇒
    for {
      user <- Query(Users).where(u ⇒ u.email === email).firstOption
      if BCrypt.checkpw(password, user.passwdHash)
    } yield user
  }
  
}
