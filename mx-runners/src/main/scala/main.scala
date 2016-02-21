import java.io.FileInputStream
import java.security.KeyStore

import com.mchange.v2.c3p0.ComboPooledDataSource
import com.typesafe.config.{Config, ConfigFactory}

import scala.slick.driver.H2Driver
import scala.slick.session.{Database, Session}

import org.slf4j.LoggerFactory
import org.h2.tools.{ Server ⇒ H2 }

import io.netty.util.internal.logging.{InternalLoggerFactory, Slf4JLoggerFactory}

import mx.common.net.{DefaultSslEngineProvider, SslEngineProvider}
import mx.domain.repo.JdbcMxRepository
import mx.smtp.SmtpServer
import mx.imap.ImapServer
import mx.web.WebApiServer

object Servers {
  def getSslEngineProvider(conf: Config) = {
    val keystore = KeyStore.getInstance("JKS")
    val keystorePass = conf.getString("sslKeystore.password").toCharArray
    val sampleKeystore = getClass.getResourceAsStream("/sample-keystore.jks")
    keystore.load(sampleKeystore, keystorePass)
    
    new DefaultSslEngineProvider(keystore, keystorePass)
  }

  def getH2Server(conf: Config) = new H2Server(conf.getInt("h2.tcpPort"), conf.getInt("h2.webPort"))
  
  def getMxRepo(conf: Config) = {
    val ds = new ComboPooledDataSource
    //TODO: take custom pool settings from application.conf, failure and retry
    ds.setJdbcUrl(conf.getString("jdbcMxRepo.url"))
    ds.setDriverClass(conf.getString("jdbcMxRepo.driverClass"))
    ds.setUser(conf.getString("jdbcMxRepo.user"))
    ds.setPassword(conf.getString("jdbcMxRepo.password"))
    
    new JdbcMxRepository(H2Driver, Database.forDataSource(ds))
  }
  
  def getMxServer(conf: Config, sslEngineProvider: SslEngineProvider, mxRepo: JdbcMxRepository) = new SmtpServer(sslEngineProvider, conf.getString("smtp.bindAddress"), conf.getInt("smtp.port"), 32, mxRepo)  
  def getImapServer(conf: Config, sslEngineProvider: SslEngineProvider, mxRepo: JdbcMxRepository) = new ImapServer(sslEngineProvider, conf.getString("imap.bindAddress"), conf.getInt("imap.port"), 32, mxRepo)
  def getWebApiServer(conf: Config, sslEngineProvider: SslEngineProvider, mxRepo: JdbcMxRepository) = new WebApiServer(sslEngineProvider, conf.getString("webApi.bindAddress"), conf.getInt("webApi.port"), 32, mxRepo)
  def getStaticFileServer(conf: Config, sslEngineProvider: SslEngineProvider, mxRepo: JdbcMxRepository) = new mx.web.StaticFileServer(sslEngineProvider, conf.getString("webClient.bindAddress"), conf.getInt("webClient.port"), 32, conf.getString("webClient.root"), conf.getInt("webClient.cacheSeconds"))
}

object RunServers extends App {
  import Servers._
  
  val logger = LoggerFactory.getLogger(RunServers.getClass)

  val conf = ConfigFactory.load()
  
  val h2Server = getH2Server(conf)
  val mxRepo = getMxRepo(conf)
  val sslEngineProvider = getSslEngineProvider(conf)
  
  val mxServer = getMxServer(conf, sslEngineProvider, mxRepo)
  val imapServer = getImapServer(conf, sslEngineProvider, mxRepo)
  val webApiServer = getWebApiServer(conf, sslEngineProvider, mxRepo)
  val staticFileServer = getStaticFileServer(conf, sslEngineProvider, mxRepo)
  
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())
  
  h2Server.start()
  mxRepo.init()
  
  if (!mxRepo.userEmailExists(conf.getString("jdbcMxRepo.seedUserEmail"))) {
    mxRepo.createUser(conf.getString("jdbcMxRepo.seedUserEmail"), conf.getString("jdbcMxRepo.seedUserPassword"))
    val user = mxRepo.getUserByEmail(conf.getString("jdbcMxRepo.seedUserEmail")).get
    mxRepo.createFolder(user.id.get, "INBOX")
    mxRepo.createFolder(user.id.get, "SENT")
    mxRepo.createFolder(user.id.get, "TRASH")
    logger.info("Created seed user: [{}]", user)
  }

//  mxServer.start()
//  webApiServer.start()
//  staticFileServer.start()

  imapServer.start()
}
