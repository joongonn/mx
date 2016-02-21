package mx.imap.parse

import scala.util.parsing.combinator.RegexParsers

trait AStringParsing { self: MxParser ⇒
  import mx.imap.command.CommandDef
  
  case class CommandInput(tag: String, cmdDef: CommandDef, arguments: List[String])
  
  import mx.imap.command._
  import ParseError._
  import ParseOk._

  protected[parse] sealed trait AString
  protected[parse] case class StringArg(value: String) extends AString
  protected[parse] case class LiteralArg(length: Int) extends AString
  
  private val Number = """\d+""".r
  private val AStringChars = """([^ (){%*\"\\]+)(?= |$)""".r // AtomChar | RespSpecials
  private val QuotedChar = """([^\r\n\"\\]|\\[\"\\])*""".r

  private def quoted: Parser[StringArg] = DQOUTE ~> QuotedChar <~ DQOUTE ^^ { StringArg(_) }
  private def literal: Parser[LiteralArg] = "{" ~> Number <~ "}$".r ^^ { n ⇒ LiteralArg(n.toInt) }
  private def string: Parser[AString] = quoted | literal
  
  def aString: Parser[AString] = commit(AStringChars ^^ { StringArg(_) } | string)

  def toStringArgsAndLiteral(args: List[AString]): (List[String], Option[Int]) = args match {
    case Nil ⇒ (Nil, None)
    case stringArgs :+ LiteralArg(n) ⇒ (stringArgs map { _.asInstanceOf[StringArg].value }, Some(n))
    case stringArgs ⇒ (stringArgs map { _.asInstanceOf[StringArg].value }, None)
  }
  
  def readOffLiteral(len: Int): Parser[String] = ("(.{" + len + "})(?= |$)").r ^^ { literal ⇒ literal }

  def literalReader(tag: String, charsToConsume: Int)(implicit onFinishedReading: (String, Option[Input]) ⇒ Either[ParseError, ParseOk]): InputParser = literalReader(tag, new StringBuilder, charsToConsume)
  
  private def literalReader(tag: String, literalAccum: StringBuilder, charsToConsume: Int)(implicit onFinishedReading: (String, Option[Input]) ⇒ Either[ParseError, ParseOk]): InputParser = {
    def read(line: String) =
      if (line.length == charsToConsume) {
        /* Eg.
         *   1) C: a01 LOGIN "fred foobar" {7}
         *   2) S: + Ready
         *   3) C: fat man
         */
        val completedLiteral = literalAccum.append(line).toString
        onFinishedReading(completedLiteral, None)
      }
      else if (line.length > charsToConsume) {
        /* Eg.
         *    1) C: a01 LOGIN {11}
         *    2) S: + Ready
         *   3a) C: fred foobar {7}  -or-  3b) C: fred foobar "fat man"  
         */
        parse(readOffLiteral(charsToConsume), line) match {
          case Success(literal, remainingInput) ⇒ 
            val completedLiteral = literalAccum.append(literal).toString
            onFinishedReading(completedLiteral, if (remainingInput.atEnd) None else 
              Some(remainingInput))
          case _ ⇒
            Left(InvalidArguments(tag))
        }
      }
      else {
        val charsLeft = charsToConsume - line.length - 2
        if (charsLeft >= 0) {
          Right(ContinueReadingLiteral(literalReader(tag, literalAccum.append(line).append("\r\n"), charsLeft)))
        } else {
          Left(InvalidArguments(tag))
        }
      }
    
    read
  }
}
