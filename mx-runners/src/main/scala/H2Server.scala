import org.slf4j.LoggerFactory

import org.h2.tools.{ Server ⇒ H2 }

class H2Server(tcpPort: Int, httpPort: Int) {
  private val logger = LoggerFactory.getLogger(classOf[H2Server])

  val h2WebServer = H2.createWebServer("-webPort", httpPort.toString)
  val h2TcpServer = H2.createTcpServer("-tcpPort", tcpPort.toString)
    
  def start() = {
    logger.info("Embedded H2 server starting on tcpPort:[{}] & webPort:[{}] ...", tcpPort, httpPort)
    h2WebServer.start()
    h2TcpServer.start()
  }
}
