package mx.imap

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

class ImapServer(sslEngineProvider: SslEngineProvider, host: String, port: Int, backlog: Int, repo: JdbcMxRepository) {
  private val logger = LoggerFactory.getLogger(classOf[ImapServer])
  
  private val StringDecoder = new StringDecoder(CharsetUtil.US_ASCII)
  private val StringEncoder = new StringEncoder(CharsetUtil.US_ASCII)
  
  private var bindChannel: Option[Channel] = None

  def start(): Unit = {
    logger.info("Listening on [{}:{}]", host, port)

    val server = Netty.bootstrap(1, 2, host, port, backlog) { pipeline ⇒ pipeline
      .addLast(new DelimiterBasedFrameDecoder(32 * 1024, true, Delimiters.lineDelimiter():_*))
      .addLast(StringDecoder)
      .addLast(StringEncoder)
      .addLast(new ReadTimeoutHandler(30 * 60))
      .addLast(new DefaultEventExecutorGroup(4), ConnectionHandler(repo, sslEngineProvider))
    }

    bindChannel = Some(server.bind())
  }
  
  def stop(): Unit = bindChannel.get.close()
}
