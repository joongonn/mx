package mx.imap.parse

import scala.util.parsing.combinator.RegexParsers

object ClientInputParser extends MxParser {
  import mx.imap.command._
  import ParseError._
  import ParseOk._

  private val Tag = """([^ (){%*\"\\\+]+)(?= |$)""".r // AtomChar + RespSpecials, except '+'
  private val RecognizedCommands = ("""(?i)\b(""" + *.map(_.name).mkString("|") + """)\b""").r
  private val SomeArgs = """.+""".r
  
  private[parse] def tagParser: Parser[String] = Tag <~ opt(SP)
  private[parse] def commandParser: Parser[CommandDef] = RecognizedCommands ^^ { getCommandDef(_).get }
  private[parse] def someArgsParser: Parser[String] = SP ~> SomeArgs 

  private def parseTag(line: String): ParseInputResult[(String, Input)] = parse(tagParser, line) match {
    case Success(tag, remaining) ⇒ Right(tag, remaining)
    case _ ⇒ Left(InvalidTag)
  }
    
  private def parseCommandDef(tag: String, remaining: Input): ParseInputResult[(CommandDef, Input)] = parse(commandParser, remaining) match {
    case Success(cmd, remaining) ⇒ Right((cmd, remaining))
    case _ ⇒ Left(InvalidCommand(tag))
  }
  
  private def checkArgs(tag: String, cmdDef: CommandDef, argsInput: Input): ParseInputResult[Option[String]] = cmdDef match {
    case cmdDef: NeedsArgsCommandDef ⇒
      if (argsInput.atEnd) {
        Left(NotEnoughArguments(tag))
      } else parse(someArgsParser, argsInput) match {
        case Success(args, _) ⇒
          Right(Some(args))
        case _ ⇒
          Left(InvalidArguments(tag))
      }
    case _: NoArgsCommandDef ⇒
      if (argsInput.atEnd) {
        Right(None)
      } else {
        Left(TooManyArguments(tag))
      }
  }
  
  private def parseArgs(tag: String, cmdDef: CommandDef, argsInput: Option[String]): ParseInputResult[ParseOk] = cmdDef match {
    case cmdDef: NoArgsCommandDef ⇒
      Right(RunnableCommand(cmdDef.makeCommand(tag)))
    case cmdDef: NeedsArgsCommandDef ⇒
      cmdDef.parseArguments(tag, argsInput.get)
  }
    
  def commandParser(line: String): Either[ParseError, ParseOk] = 
    for {
      tagPart <- parseTag(line).right
      cmdDefPart <- parseCommandDef(tagPart._1, tagPart._2).right
      argsInput <- checkArgs(tagPart._1, cmdDefPart._1, cmdDefPart._2).right
      result <- parseArgs(tagPart._1, cmdDefPart._1, argsInput).right
    } yield result
}
