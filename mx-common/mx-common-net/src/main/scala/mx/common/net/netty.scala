package mx.common.net

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener, ChannelHandler, ChannelInitializer, ChannelOption, ChannelPipeline}

object Netty {
  private def wrapOnSuccess(block: Channel ⇒ Unit) = new ChannelFutureListener() {
    override def operationComplete(f: ChannelFuture) = if (f.isDone && f.isSuccess) block(f.channel)
  }

  private def wrapOnFailure(block: Channel ⇒ Unit) = new ChannelFutureListener() {
    override def operationComplete(f: ChannelFuture) = if (f.isDone && !f.isSuccess) block(f.channel)
  }
  
  private def wrapOnComplete(block: Channel ⇒ Unit) = new ChannelFutureListener() {
    override def operationComplete(f: ChannelFuture) = if (f.isDone) block(f.channel)
  }
  
  implicit class MxChannelFuture(val self: ChannelFuture) extends AnyVal {
    def onSuccess(block: Channel ⇒ Unit) = self.addListener(wrapOnSuccess(block))
    
    def onFailure(block: Channel ⇒ Unit) = self.addListener(wrapOnFailure(block))
    
    def onComplete(block: Channel ⇒ Unit) = self.addListener(wrapOnComplete(block))
  }
  
  def bootstrap(bossGrpThreads: Int, workerGrpThreads: Int, host: String, port: Int, backlog: Int)(initChannelPipeline: ChannelPipeline ⇒ Unit) = {
    new NettyServer(bossGrpThreads, workerGrpThreads, host, port, backlog, initChannelPipeline)
  }
}

class NettyServer(bossGrpThreads: Int, workerGrpThreads: Int, host: String, port: Int, backlog: Int, initChannelPipeline: ChannelPipeline ⇒ Unit) {
  import Netty._
  
  private val bootstrap = new ServerBootstrap()
  private val bossGrp = new NioEventLoopGroup(bossGrpThreads)
  private val workerGrp = new NioEventLoopGroup(workerGrpThreads)
  
  bootstrap
      .group(bossGrp, workerGrp)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new ChannelInitializer[SocketChannel]() {
         override def initChannel(channel: SocketChannel): Unit = initChannelPipeline(channel.pipeline)
       })
      .option[java.lang.Integer](ChannelOption.SO_BACKLOG, backlog)
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)

      
  def bind(): Channel = {
    val f = bootstrap.bind(host, port).sync()
    f.channel.closeFuture.onSuccess { _ ⇒ shutdownGracefully() } 
    f.channel
  }
  
  private def shutdownGracefully(): Unit = {
    workerGrp.shutdownGracefully()
    bossGrp.shutdownGracefully()
  }
}
