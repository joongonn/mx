package mx.common

object Logging {
  implicit class LoggerArrayArgs(val self: Seq[Any]) extends AnyVal {
    def toLoggerArgs: Array[Object] = self.map(_.asInstanceOf[AnyRef]).toArray[AnyRef]
  }
}