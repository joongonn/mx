package mx.common

import java.nio.ByteBuffer
import java.util.Random

object ConnectionSession {
  private val rand = new Random()
    
  def newLoggingId() = {
    val buff = ByteBuffer.allocate(4)
    rand.nextBytes(buff.array)
    BigInt(1, buff.array).toString(16)
  }
  
  def apply() = new ConnectionSession(newLoggingId(), false)
}

class ConnectionSession(val id: String, var tlsStarted: Boolean) {
  import org.slf4j.Logger
  import mx.common.Logging._
  
  def trace(msg: String)(implicit logger: Logger) = logger.trace(s"${id}: ${msg}")
  def trace(msg: String, args: Any*)(implicit logger: Logger) = logger.trace(s"${id}: ${msg}", args.toLoggerArgs)
  def debug(msg: String)(implicit logger: Logger) = logger.debug(s"${id}: ${msg}")
  def debug(msg: String, args: Any*)(implicit logger: Logger) = logger.debug(s"${id}: ${msg}", args.toLoggerArgs)
  def info(msg: String)(implicit logger: Logger) = logger.info(s"${id}: ${msg}")
  def info(msg: String, args: Any*)(implicit logger: Logger) = logger.info(s"${id}: ${msg}", args.toLoggerArgs)
  def warn(msg: String)(implicit logger: Logger) = logger.warn(s"${id}: ${msg}")
  def warn(msg: String, args: Any*)(implicit logger: Logger) = logger.warn(s"${id}: ${msg}", args.toLoggerArgs)
  def error(msg: String)(implicit logger: Logger) = logger.error(s"${id}: ${msg}")
  def error(msg: String, args: Any*)(implicit logger: Logger) = logger.error(s"${id}: ${msg}", args.toLoggerArgs)
}
