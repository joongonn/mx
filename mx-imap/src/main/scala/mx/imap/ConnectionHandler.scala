package mx.imap;

import org.slf4j.LoggerFactory

import io.netty.channel.{ ChannelFuture, ChannelFutureListener, ChannelHandlerContext }

import mx.common.net.{ SslEngineProvider, TextBasedChannel }
import mx.domain.repo.JdbcMxRepository

import parse._
import command._
import response._

object ConnectionHandler {
  sealed abstract class HandleResult

  type InputHandler = (String) ⇒ HandleResult
  
  case class BeginContinuation(nextInputHandler: InputHandler) extends HandleResult
  case class Continuation(nextInputHandler: InputHandler) extends HandleResult
  case class ResponseResult(responses: List[Response], nextInputHandler: InputHandler) extends HandleResult

  def apply(repo: JdbcMxRepository, sslEngineProvider: SslEngineProvider) = new ConnectionHandler(sslEngineProvider)(repo)
}

class ConnectionHandler(sslEngineProvider: SslEngineProvider)(implicit repo: JdbcMxRepository) extends TextBasedChannel(sslEngineProvider) {
  import parse.InputParser
  import ClientInputParser.commandParser
  import ConnectionHandler._
  
  private implicit var session = Session()
  private var handleInput: InputHandler = expectNextCommand
  
  override def onConnected = writeResponse(Untagged("OK mx Ready"))
  
  override def onReadln(line: String): Unit = handleInput(line) match {
    case BeginContinuation(nextHandler) ⇒
      handleInput = nextHandler
      writeln("+ Ready")
    case Continuation(nextHandler) ⇒
      handleInput = nextHandler
    case ResponseResult(responses, nextHandler) ⇒
      handleInput = nextHandler
      writeResponse(responses)
  }
  
  private def expectNextCommand = parseInput(commandParser)
  
  private def parseInput(parse: InputParser): InputHandler = {
    import ParseOk._
    import ParseError._
    import CompletionResult._
    
    parse andThen {
      case Right(parseOk) ⇒
        parseOk match {
          case ReadLiteral(nextParser) ⇒
            BeginContinuation(parseInput(nextParser))
          case ContinueReadingLiteral(nextParser) ⇒
            Continuation(parseInput(nextParser))
          case RunnableCommand(cmd) ⇒
            runCommand(cmd)
        }
      case Left(parseError) ⇒
        val response = parseError match {
          case InvalidTag ⇒
            Untagged("BAD invalid tag")
          case InvalidCommand(tag) ⇒
            Tagged(tag, Bad("Unrecognized command"))
          case InvalidArguments(tag) ⇒
            Tagged(tag, Bad("Unable to parse arguments"))
          case TooManyArguments(tag) ⇒
            Tagged(tag, Bad("Too many arguments"))
          case NotEnoughArguments(tag) ⇒
            Tagged(tag, Bad("Not enough arguments"))
        }
        ResponseResult(response, expectNextCommand)
    }
  }
  
  private def runCommand(command: Command): HandleResult = {
    import RunResult._

    connectionSession.debug("Running command: [{}]", command)
    
    def getContinuationData(cmd: RunContinuationData): InputHandler = cmd.runWithContinuationData _ andThen {
      case Completed(newSession, responses) ⇒
        session = newSession
        ResponseResult(responses, expectNextCommand)
      case NeedsContinuationData(cmd: RunContinuationData) ⇒
        Continuation(getContinuationData(cmd))
    }
    
    command.run() match {
      case Completed(newSession, responses) ⇒
        session = newSession
        ResponseResult(responses, expectNextCommand)
      case NeedsContinuationData(cmd: RunContinuationData) ⇒
        BeginContinuation(getContinuationData(cmd)) 
    }
  }
  
  private def writeResponse(response: List[Response]): Unit = {
    import CompletionResult._
    
    response.foreach {
      case Untagged(line) ⇒
        writeln(s"* ${line}")
      case Tagged(tag, Ok(line)) ⇒
        writeln(s"${tag} OK ${line}")
      case Tagged(tag, No(line)) ⇒
        writeln(s"${tag} NO ${line}")
      case Tagged(tag, Bad(line)) ⇒
        writeln(s"${tag} BAD ${line}")
    }
  }
}
