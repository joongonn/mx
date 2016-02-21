package mx.smtp.delivery

import java.util.concurrent.{TimeUnit, TimeoutException}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise, future}
import scala.util.{Success, Failure}

import io.netty.util.{HashedWheelTimer, TimerTask, Timeout}

import mx.domain.repo.JdbcMxRepository

sealed trait CheckResult
trait CheckFail extends CheckResult
case object Accepted extends CheckResult
case object Rejected extends CheckFail
case class Error(cause: Throwable) extends CheckResult

//If auth-ed, allow relay
trait SenderChecking {
  def checkSender(fromAddr: String)(timeout: Duration)(onSenderCheckComplete: CheckResult ⇒ Unit): Unit
}

trait RecipientChecking {
  def checkRecipient(toAddr: String)(timeout: Duration)(onRecipientCheckComplete: CheckResult ⇒ Unit): Unit
}

trait EnvelopeChecking {
  import mx.smtp.inbound.Envelope
  
  def checkEnvelope(envelope: Envelope)(timeout: Duration)(onEnvelopeCheckComplete: CheckResult ⇒ Unit): Unit
}

trait DeliverySystem {
  import mx.smtp.inbound.Envelope
  
  private val timer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS)
  
  protected val repo: JdbcMxRepository
  
  timer.start()

  private def scheduleTimeout(promise: Promise[_], after: Duration): Unit = timer.newTimeout(new TimerTask {
    def run(timeout: Timeout) = if (!promise.isCompleted) {
      promise.failure(new TimeoutException(s"Timed out after ${after.toMillis}ms"))
    }
  }, after.toMillis, TimeUnit.MILLISECONDS)
  
  private def futureWithTimeout[T](after: Duration, block: ⇒ T)(implicit ctx: ExecutionContext): Future[T] = {
    val promise = Promise[T]
    promise.tryCompleteWith(future(block))
    scheduleTimeout(promise, after)
    promise.future
  }

  protected def executeCheck[L <: CheckFail](timeout: Duration)(checkBlock: ⇒ Either[L, Unit])(onCheckComplete: CheckResult ⇒ Unit)(implicit ctx: ExecutionContext): Unit = {
    futureWithTimeout(timeout, checkBlock) onComplete {
      case Success(either) ⇒ either match {
        case Right(_) ⇒ onCheckComplete(Accepted)
        case Left(reason) ⇒ onCheckComplete(reason)
      }
      case Failure(t) ⇒ onCheckComplete(Error(t))
    }
  }
  
  def queueForDelivery(envelope: Envelope): Unit
}
