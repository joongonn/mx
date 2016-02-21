import com.typesafe.config.{Config, ConfigFactory}

import org.apache.commons.daemon.{Daemon, DaemonContext}

import org.slf4j.LoggerFactory

import io.netty.util.internal.logging.{InternalLoggerFactory, Slf4JLoggerFactory}

import mx.common.net.{DefaultSslEngineProvider, SslEngineProvider}
import mx.domain.repo.JdbcMxRepository
import mx.smtp.SmtpServer
import mx.imap.ImapServer
import mx.web.WebApiServer

class RunServersAsDaemon extends Daemon {
  import Servers._
  
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())
  
  private var conf: Config = null
  
  def init(ctx: DaemonContext): Unit = {
    conf = ConfigFactory.load()

    val h2Server = getH2Server(conf)
    val mxRepo = getMxRepo(conf)
    val sslEngineProvider = getSslEngineProvider(conf)
  
    val mxServer = getMxServer(conf, sslEngineProvider, mxRepo)
    val imapServer = getImapServer(conf, sslEngineProvider, mxRepo)
    val webApiServer = getWebApiServer(conf, sslEngineProvider, mxRepo)
    val staticFileServer = getStaticFileServer(conf, sslEngineProvider, mxRepo)

    h2Server.start()
    mxRepo.init()

    if (!mxRepo.userEmailExists(conf.getString("jdbcMxRepo.seedUserEmail"))) {
      mxRepo.createUser(conf.getString("jdbcMxRepo.seedUserEmail"), conf.getString("jdbcMxRepo.seedUserPassword"))
      val user = mxRepo.getUserByEmail(conf.getString("jdbcMxRepo.seedUserEmail")).get
      mxRepo.createFolder(user.id.get, "INBOX")
      mxRepo.createFolder(user.id.get, "SENT")
      mxRepo.createFolder(user.id.get, "TRASH")
    }
    
    mxServer.start()
    imapServer.start()
    webApiServer.start()
    staticFileServer.start()
  }
  
  def start(): Unit = {
  }
  
  def stop(): Unit = {
  }
  
  def destroy(): Unit = {
  }
}
