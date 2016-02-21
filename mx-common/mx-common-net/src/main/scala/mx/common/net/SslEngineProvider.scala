package mx.common.net

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManager, TrustManagerFactory}
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

trait SslEngineProvider {
  def createEngine(useClientMode: Boolean): SSLEngine
  def createServerEngine(): SSLEngine
  def createClientEngine(): SSLEngine
}

object TrustAll {
  val trustAll = Array[TrustManager] (
      new X509TrustManager {
        override def getAcceptedIssuers() = null
        override def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = ()
        override def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = ()
      }
  )
}

class DefaultSslEngineProvider(keystore: KeyStore, keystorePassword: Array[Char]) extends SslEngineProvider {
  private val secureRnd = new SecureRandom()
  private val trustManager = TrustManagerFactory.getInstance("SunX509")
  private val keyManager = KeyManagerFactory.getInstance("SunX509");
  private val sslCtx = SSLContext.getInstance("TLSv1.1")
  
  trustManager.init(keystore)
  keyManager.init(keystore, keystorePassword)
  // sslCtx.init(keyManager.getKeyManagers, trustManager.getTrustManagers, secureRnd)
  sslCtx.init(keyManager.getKeyManagers, TrustAll.trustAll, secureRnd)
  
  def createEngine(useClientMode: Boolean) = {
    val engine = sslCtx.createSSLEngine()
    engine.setUseClientMode(useClientMode)
    engine
  }
  
  def createServerEngine() = {
    val engine = sslCtx.createSSLEngine()
    engine.setUseClientMode(false)
    engine
  }

  def createClientEngine() = {
    val engine = sslCtx.createSSLEngine()
    engine.setUseClientMode(true)
    engine
  }
}