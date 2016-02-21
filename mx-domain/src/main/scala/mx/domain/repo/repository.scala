package mx.domain.repo

import scala.slick.driver.ExtendedProfile
import scala.slick.session.{Database, Session}

import org.slf4j.LoggerFactory

import mx.domain.repo.local.{FolderRepo, FolderMailRepo, UserRepo}
import mx.domain.repo.delivery.{MailDataRepo, MailQRepo}

trait HasDB {
  val db: Database
}

class JdbcMxRepository(val driver: ExtendedProfile, val db: Database) extends Schema with HasDB with UserRepo with FolderRepo with FolderMailRepo with MailDataRepo with MailQRepo {
  import driver.simple._
  
  private val logger = LoggerFactory.getLogger(classOf[JdbcMxRepository])

  def init() = db.withSession { implicit session: Session ⇒
    import scala.slick.jdbc.meta._
    
    Tables.foreach { t ⇒
      if (MTable.getTables(t.tableName).list().isEmpty) {
        logger.info(s"Creating [${t.tableName}] Table ...")
        t.ddl.create
      }
    }
  }
}
