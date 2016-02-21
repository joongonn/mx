package mx.common.net

import io.netty.channel.{ChannelFuture, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.ssl.{SslHandler, SslHandshakeCompletionEvent}

import org.slf4j.LoggerFactory

import mx.common.ConnectionSession

abstract class TextBasedChannel(sslEngineProvider: SslEngineProvider) extends SimpleChannelInboundHandler[String] {
  implicit protected val logger = LoggerFactory.getLogger(this.getClass)
  
  private var ctx: ChannelHandlerContext = null
  protected val connectionSession = ConnectionSession()
  
  final override def channelActive(ctx: ChannelHandlerContext) = {
    connectionSession.info("Connected [{}]", ctx.channel.remoteAddress)
    this.ctx = ctx
    onConnected
  }

  final override def channelInactive(ctx: ChannelHandlerContext) = connectionSession.info("Disconnected [{}]", ctx.channel.remoteAddress)
  
  final override def channelRead0(ctx: ChannelHandlerContext, line: String) = {
    connectionSession.debug("Received: [{}]", line)
    onReadln(line)
  }
  
  def onConnected = ()
  
  def addTlsHandler(useClientMode: Boolean) = {
    val sslHandler = new SslHandler(sslEngineProvider.createEngine(useClientMode))
    ctx.channel.pipeline.addFirst(sslHandler)
  }
  
  def writeln(line: String): Option[ChannelFuture] = if (ctx.channel.isActive) {
    connectionSession.debug("Writing: [{}]", line)
    Some(ctx.writeAndFlush(line + "\r\n"))
  } else {
    connectionSession.debug("Channel already closed, discarding write:[{}]", line)
    None
  }
  
  def onReadln(line: String)
 
  final override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any) = evt match {
    case tlsCompleted: SslHandshakeCompletionEvent ⇒
      if (tlsCompleted.isSuccess) {
        onTlsHandshakeCompleted(Right(()))
      } else {
        onTlsHandshakeCompleted(Left(tlsCompleted.cause))
      }
    case _ ⇒ onUserEvent(evt)
  }
  
  def onTlsHandshakeCompleted(result: Either[Throwable, Unit]): Unit = ()
  
  def onUserEvent(evt: Any): Unit = ()
  
  def disconnect() = ctx.close()
}
