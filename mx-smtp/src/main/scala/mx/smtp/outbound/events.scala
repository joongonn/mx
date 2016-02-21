package mx.smtp.outbound

sealed trait Event

case object TlsHandshakeCompleted extends Event
case class Send(envelope: Envelope, onComplete: ClientChannelHandler.OnComplete) extends Event

sealed trait ServerResponse extends Event

case object ServerReady extends ServerResponse
case class Ok(status: String, message: String) extends ServerResponse
case class OkMore(status: String, message: String) extends ServerResponse
case object StartMailInput extends ServerResponse

case class TemporaryFailure(status: String, message: String) extends ServerResponse
case class PermanentFailure(status: String, message: String) extends ServerResponse

object ServerResponse {
  private object Matcher {
    val MailActionOk     = """^(2\d\d)([ -]?)(.*)$""".r
    val Ready            = """^220 .*$""".r
    val StartMailInput   = """^354 .*$""".r
    val TemporaryFailure = """^(4\d\d) (.*)$""".r
    val PermanentFailure = """^(5\d\d) (.*)$""".r
  }

  implicit class ResponseWrapper(val self: String) extends AnyVal {
    def toServerResponse: ServerResponse = self match {
      case Matcher.Ready() ⇒ ServerReady
      case Matcher.MailActionOk(status, dash, content) ⇒ if (dash == "-") OkMore(status, content) else Ok(status, content)
      case Matcher.StartMailInput() ⇒ StartMailInput
      case Matcher.TemporaryFailure(status, content) ⇒ TemporaryFailure(status, content)
      case Matcher.PermanentFailure(status, content) ⇒ PermanentFailure(status, content)
    }
  }
}
