package mx.smtp

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import org.slf4j.LoggerFactory

import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.{Delimiters, DelimiterBasedFrameDecoder}
import io.netty.handler.codec.string.{StringDecoder, StringEncoder}
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.concurrent.DefaultEventExecutorGroup
import io.netty.util.CharsetUtil

import mx.common.net.{Netty, SslEngineProvider}
import mx.domain.repo.JdbcMxRepository
import mx.smtp.delivery.{DefaultDeliverySystem}
import mx.smtp.inbound.ConversationChannelHandler
import mx.smtp.outbound.SendMail

class SmtpServer(sslEngineProvider: SslEngineProvider, host: String, port: Int, backlog: Int, repo: JdbcMxRepository) {
  private val logger = LoggerFactory.getLogger(classOf[SmtpServer])
  
  private val StringDecoder = new StringDecoder(CharsetUtil.US_ASCII)
  private val StringEncoder = new StringEncoder(CharsetUtil.US_ASCII)
  
  private var bindChannel: Option[Channel] = None

  private val deliverySystem = new DefaultDeliverySystem(repo)(ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8)))
  private val sendMail = new SendMail(repo, sslEngineProvider)
  private val handlerExecutor = new DefaultEventExecutorGroup(4)
  private val server = Netty.bootstrap(1, 2, host, port, backlog) { pipeline ⇒ pipeline
      .addLast(new DelimiterBasedFrameDecoder(32 * 1024, true, Delimiters.lineDelimiter():_*))
      .addLast(StringDecoder)
      .addLast(StringEncoder)
      .addLast(new ReadTimeoutHandler(30))
      .addLast(handlerExecutor, new ConversationChannelHandler(sslEngineProvider, deliverySystem))
    }
  
  def start(): Unit = {
    logger.info("Listening on [{}:{}]", host, port)
    bindChannel = Some(server.bind())
    val sendMailThread = new Thread(sendMail);
    sendMailThread.setDaemon(true)
    sendMailThread.start()
  }
  
  def stop(): Unit = bindChannel.get.close()
}
