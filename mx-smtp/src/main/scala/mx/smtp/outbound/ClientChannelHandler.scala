package mx.smtp.outbound

import io.netty.channel.ChannelHandlerContext

import mx.common.net.{TextBasedChannel, SslEngineProvider}

object ClientChannelHandler {
  type OnComplete = Either[Unit, Unit] ⇒ Unit
  
  sealed trait Handler {
    def handle(evt: Event): Handled
    def start(): Unit
  }

  sealed trait Handled
  
  case object Cont extends Handled
  case class Done(nextHandler: Handler) extends Handled
  case object Quit extends Handled
}

class ClientChannelHandler(sslEngineProvider: SslEngineProvider) extends TextBasedChannel(sslEngineProvider) {
  import ClientChannelHandler._
  
  private var handler = connected
  
  def send(envelope: Envelope)(onComplete: OnComplete) = handleEvent(Send(envelope, onComplete))
  
  override def onReadln(line: String) = {
    import ServerResponse._
    
    connectionSession.debug("Received: [{}]", line)
    handleEvent(line.toServerResponse)
  }

  private def handleEvent(evt: Event) = handler.handle(evt) match {
    case Cont ⇒ 
    case Done(nextHandler) ⇒
      handler = nextHandler
      handler.start()
    case Quit ⇒ disconnect()
  }
  
  private def connected: Handler = new Handler {
    @volatile var serverReady = false
    @volatile implicit var send: Option[Send] = None
    
    def start() = ()
    def handle(evt: Event) = evt match {
      case ServerReady ⇒
        serverReady = true
        send match {
          case Some(s) ⇒ Done(ehlo(s.envelope, s.onComplete))
          case None ⇒ Cont
        } 
      case s: Send ⇒
        send = Some(s) 
        if (serverReady) Done(ehlo(s.envelope, s.onComplete)) else Cont
    }
  }
  
  private def ehlo(implicit envelope: Envelope, onComplete: OnComplete) = new Handler {
    def start() = writeln("EHLO world")
    def handle(evt: Event) = evt match {
      case _: OkMore ⇒ Cont
      case _: Ok ⇒ Done(startTls)
    }
  }
  
  private def startTls(implicit envelope: Envelope, onComplete: OnComplete) = new Handler {
    def start() = writeln("STARTTLS")
    def handle(evt: Event) = evt match {
      case ServerReady ⇒
        addTlsHandler(true)
        Cont
      case TlsHandshakeCompleted ⇒ Done(setReturnPath)
    }
  }
  
  private def setReturnPath(implicit envelope: Envelope, onComplete: OnComplete) = new Handler {
    def start() = writeln(s"MAIL FROM:<${envelope.returnPath}>")
    def handle(evt: Event) = evt match {
      case _: Ok ⇒ Done(setRecipients(envelope.recipients.toList))
    }
  }
  
  private def setRecipients(recipients: List[String])(implicit envelope: Envelope, onComplete: OnComplete): Handler = recipients match {
    case x :: xs ⇒ new Handler {
      def start() = writeln(s"RCPT TO:<${x}>")
      def handle(evt: Event) = evt match {
        case _: Ok ⇒ if (xs == Nil) Done(sendData) else Done(setRecipients(xs)) 
      }
    }
  }
  
  private def sendData(implicit envelope: Envelope, onComplete: OnComplete) = new Handler {
    def start() = writeln("DATA")
    def handle(evt: Event) = evt match {
      case StartMailInput ⇒
        writeln(envelope.mailData) //FIXME: dot unstuffing
        writeln(".")
        Cont
      case _: Ok ⇒
        onComplete(Right(()))
        Done(quit)
    }
  }
  
  private def quit = new Handler {
    def start() = writeln("QUIT")
    def handle(evt: Event) = Quit
  }
  
  override def onTlsHandshakeCompleted(result: Either[Throwable, Unit]) = result match {
    case Right(_) ⇒
      connectionSession.debug("TLS handshake completed")
      handleEvent(TlsHandshakeCompleted)
    case Left(cause) ⇒
      connectionSession.warn("TLS handshake failed:", cause)
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    logger.warn("Exception caught", cause)
    ctx.close()
  }
}
