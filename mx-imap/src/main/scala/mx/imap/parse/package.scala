package mx.imap

import scala.util.parsing.combinator.RegexParsers

import mx.imap.command.{ Command, CommandDef }

package object parse {
  sealed abstract class ParseOk
  sealed abstract class ParseError

  type InputParser = (String) ⇒  Either[ParseError, ParseOk]
  
  object ParseOk {
    case class ReadLiteral(nextParser: InputParser) extends ParseOk
    case class ContinueReadingLiteral(nextParser: InputParser) extends ParseOk
    case class RunnableCommand(command: Command) extends ParseOk
  }
  
  object ParseError {
    case object InvalidTag extends ParseError
    case class InvalidCommand(tag: String) extends ParseError
    case class InvalidArguments(tag: String) extends ParseError
    case class NotEnoughArguments(tag: String) extends ParseError
    case class TooManyArguments(tag: String) extends ParseError
  }

  trait MxParser extends RegexParsers {
    type ParseInputResult[R] = Either[ParseError, R]
    
    override val skipWhitespace = false
    
    val SP = " "
    val DQOUTE = "\""
    val Atom = """([^(){ %*"\\\]]+)(?= |$)""".r
  }
}
