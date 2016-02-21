package mx.smtp.outbound

import io.netty.bootstrap.Bootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, ChannelInitializer, SimpleChannelInboundHandler}
import io.netty.handler.codec.{Delimiters, DelimiterBasedFrameDecoder}
import io.netty.handler.codec.string.{StringDecoder, StringEncoder}

import org.slf4j.LoggerFactory

import mx.common.ConnectionSession
import mx.common.net.{DNS, MxRecord, SslEngineProvider}
import mx.domain.repo.{JdbcMxRepository, MailQ, MailQStatus}

case class Envelope(mxRecords: List[MxRecord], returnPath: String, recipients: Array[String], mailData: String)

class SendMail(repo: JdbcMxRepository, sslEngineProvider: SslEngineProvider) extends Runnable {
  import mx.common.net.Netty._
  
  implicit private val logger = LoggerFactory.getLogger(classOf[SendMail])
  
  private val Decoder: StringDecoder = new StringDecoder()
  private val Encoder: StringEncoder = new StringEncoder()  

  private val channelInitializer = new ChannelInitializer[NioSocketChannel] {
    override def initChannel(ch: NioSocketChannel) = ch.pipeline
        .addLast(new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter():_*))
        .addLast(Decoder)
        .addLast(Encoder)
        .addLast(new ClientChannelHandler(sslEngineProvider))
  }

  private val group = new NioEventLoopGroup(1)
  private val bootstrap = new Bootstrap()

  bootstrap
      .group(group)
      .channel(classOf[NioSocketChannel])
      .handler(channelInitializer)
      
  override def run() = {
    while (true) {
      repo.getUndeliveredQItem() match {
        case Some(item) if (repo.checkoutMailQItem(item.id.get)) ⇒
          send(item)
        case None ⇒
          Thread.sleep(1000)
      }
    }
  }
  
  def send(item: MailQ) = {
    logger.debug("Attemping to send qItem #{}", item)
    val mailData = repo.getMailDataById(item.mailDataId).get
    val envelope = Envelope(DNS.getMxRecords(item.domain), mailData.returnPath, Array(item.recipient), mailData.rfc822)
    val mxHost = envelope.mxRecords.head.host
    val f = bootstrap.connect(mxHost, 25)
    
    f onSuccess { channel ⇒
      val client = channel.pipeline.get(classOf[ClientChannelHandler]) 
      client.send(envelope) {
        case Left(_) ⇒
          repo.updateMailQItemStatus(item.id.get, true, MailQStatus.Delivered)
        case Right(_) ⇒
          repo.updateMailQItemStatus(item.id.get, true, MailQStatus.Delivered)
      }
    } onFailure { channel ⇒
      logger.info("Connect failed:[{}]", mxHost)
      channel.close()
    }
  }
}