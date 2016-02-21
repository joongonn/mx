package mx.web

import org.slf4j.LoggerFactory

import io.netty.channel.Channel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.handler.codec.http.{HttpContentCompressor, HttpObjectAggregator, HttpRequest, HttpResponse, HttpServerCodec}
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.concurrent.DefaultEventExecutorGroup

import mx.common.net.{Netty, SslEngineProvider}
import mx.domain.repo.JdbcMxRepository

class WebApiServer(sslEngineProvider: SslEngineProvider, host: String, port: Int, backlog: Int, repo: JdbcMxRepository) {
  private val logger = LoggerFactory.getLogger(classOf[WebApiServer])
  
  private var bindChannel: Option[Channel] = None
  
  def start(): Unit = {
    logger.info("Listening on [{}:{}]", host, port)

    val apiRequestHandler = new ApiRequestHandler(repo)
    val handlerExecutor = new DefaultEventExecutorGroup(4)
    val server = Netty.bootstrap(1, 2, host, port, backlog) { pipeline ⇒ pipeline
        // .addLast(new io.netty.handler.ssl.SslHandler(sslEngineProvider.createServerEngine()))
        .addLast(new HttpServerCodec())
        .addLast(new HttpObjectAggregator(4 * 1024 * 1024))
        .addLast(new HttpContentCompressor())
        .addLast(new ReadTimeoutHandler(5))
        .addLast(handlerExecutor, apiRequestHandler)
    }
    
    bindChannel = Some(server.bind())
  }
  
  def stop(): Unit = bindChannel.get.close()
}

class StaticFileServer(sslEngineProvider: SslEngineProvider, host: String, port: Int, backlog: Int, documentRoot: String, cacheSeconds: Int) {
  private val logger = LoggerFactory.getLogger(classOf[StaticFileServer])
  
  private var bindChannel: Option[Channel] = None
  
  def start() = {
    logger.info("Listening on [{}:{}] with DocumentRoot:[{}]", Array(host, port, documentRoot).asInstanceOf[Array[Object]])

    val staticFileRequestHandler = new StaticFileRequestHandler(documentRoot, cacheSeconds)
    val handlerExecutor = new DefaultEventExecutorGroup(4)
    val server = Netty.bootstrap(1, 2, host, port, backlog) { pipeline ⇒ pipeline
        // .addLast(new io.netty.handler.ssl.SslHandler(sslEngineProvider.createServerEngine()))
        .addLast(new HttpServerCodec())
        .addLast(new HttpContentCompressor())
        .addLast(new ReadTimeoutHandler(5))
        .addLast(handlerExecutor, staticFileRequestHandler)
    }
    
    bindChannel = Some(server.bind())
  }
  
  def stop(): Unit = bindChannel.get.close()
}