package mx.imap

import mx.domain.repo.JdbcMxRepository
import mx.imap.response._
import mx.imap.parse.{ AStringParsing, MxParser, ParseError, ParseOk }

package object command {
  val * = AuthenticateDef :: LoginDef :: CapabilityDef :: Nil
  
  def getCommandDef(name: String): Option[CommandDef] = *.find(_.name.equalsIgnoreCase(name))

  sealed abstract class CompletionResult

  object CompletionResult {
    case class Ok(line: String) extends CompletionResult
    case class No(line: String) extends CompletionResult
    case class Bad(line: String) extends CompletionResult
  }

  sealed abstract class RunResult

  object RunResult {
    case class Completed(session: Session, responses: List[Response]) extends RunResult
    case class NeedsContinuationData(command: RunContinuationData) extends RunResult
  }
  
  sealed abstract class CommandDef(val name: String) extends MxParser

  abstract class NoArgsCommandDef(name: String) extends CommandDef(name) {
    import mx.imap.parse.{ ParseOk, ParseError }
    import ParseError._
    import ParseOk._
  
    def makeCommand(tag: String): Command
  }
  
  abstract class NeedsArgsCommandDef(name: String) extends CommandDef(name) {
    def parseArguments(tag: String, argsInput: String): Either[ParseError, ParseOk]
  }

  abstract class Command {
    def run()(implicit session: Session, repo: JdbcMxRepository): RunResult
  }

  trait RunContinuationData { self: Command ⇒
    def runWithContinuationData(continuationData: String)(implicit session: Session, repo: JdbcMxRepository): RunResult
  }
}
