package mx.imap.command

import mx.imap.parse.AStringParsing

abstract class FixedArgsCommand(name: String, val arity: Int) extends NeedsArgsCommandDef(name) with AStringParsing {
  import scala.util.parsing.input.CharSequenceReader
  
  import mx.imap.parse.{ ParseOk, ParseError }
  import ParseError._
  import ParseOk._
  
  private def aStrings: Parser[List[AString]] = aString ~ rep(SP ~> aString) ^^ { case x ~ xs ⇒ x :: xs }
  
  def makeCommand(tag: String, completedArgs: List[String]): Command
  
  override def parseArguments(tag: String, argsInput: String): Either[ParseError, ParseOk] = {
    def finishedArguments(completedArgs: List[String]) =
      if (arity == completedArgs.length) {
        Right(RunnableCommand(makeCommand(tag, completedArgs)))
      } else if (arity > completedArgs.length) {
        Left(NotEnoughArguments(tag))
      } else {
        Left(TooManyArguments(tag))
      }
    
    def parseMore(currentArgs: List[String], remainingInput: Input): Either[ParseError, ParseOk] =
      parse(aStrings, remainingInput) match {
        case Success(aStrings, _) ⇒
          toStringArgsAndLiteral(aStrings) match {
            case (stringArgs, None) ⇒
              /* Eg.
               *   C: a01 LOGIN "fred foobar" "fat man"
               */
              finishedArguments(currentArgs ++ stringArgs)
            case (stringArgs, Some(literalLength)) ⇒
              /* Eg.
               *   C: a01 LOGIN "fred foobar" {7}
               */
              if (arity >= currentArgs.length + stringArgs.length + 1) {
                Right(startReadingLiteral(currentArgs ++ stringArgs, literalLength))
              } else {
                Left(TooManyArguments(tag))
              }
          }
        case _ ⇒
          Left(InvalidArguments(tag))
      }
    
    def startReadingLiteral(currentArgs: List[String], literalLength: Int) =
      ReadLiteral(literalReader(tag, literalLength) {
        case (literal, None) ⇒
          finishedArguments(currentArgs :+ literal)
        case (literal, Some(remainingInput)) ⇒
          parseMore(currentArgs :+ literal, remainingInput)
      })
    
    parseMore(Nil, new CharSequenceReader(argsInput))
  }
}
