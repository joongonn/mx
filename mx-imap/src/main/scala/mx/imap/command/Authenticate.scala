package mx.imap.command

import mx.imap.parse.AStringParsing

object AuthenticateDef extends NeedsArgsCommandDef("AUTHENTICATE") with AStringParsing {
  import mx.imap.parse.{ ParseOk, ParseError }
  import ParseError._
  import ParseOk._
  
  private[command] val Plain = """(?i)PLAIN""".r
  
  private def argsParser: Parser[(AString, Option[AString])] = aString ~ opt((SP ~> aString)) ^^ { case mechanism ~ auth ⇒ (mechanism,  auth) }

  private def parseAString(tag: String, input: String): ParseInputResult[(AString, Option[Input])] = parse(aString, input) match {
    case Success(result, remainingInput) ⇒ result match {
      case literal: LiteralArg ⇒
        Right((literal, None))
      case stringArg: StringArg ⇒
        Right((stringArg, if (remainingInput.atEnd) None else Some(remainingInput)))
    }
    case _ ⇒
      Left(InvalidArguments(tag))
  }
  
  override def parseArguments(tag: String, argsInput: String) = parse(argsParser, argsInput) match {
    case Success(result, remainingInput) ⇒
      result match {
        case (LiteralArg(n), _) ⇒
          Right(ReadLiteral(literalReader(tag, n) {
            case (mechanism, _) ⇒
              println("### howdy hah i am here where i need to be")
              Left(InvalidArguments(tag)) // FIXME
          }))
        case (StringArg(mechanism), auth) ⇒
          if (remainingInput.atEnd) {
            // FIXME: Right(RunnableCommand(Authenticate(tag, mechanism, auth)))
            Left(TooManyArguments(tag))
          } else {
            Left(TooManyArguments(tag))
          }
      }
    case _ ⇒
      Left(InvalidArguments(tag))
  }
}

case class Authenticate(tag: String, mechanism: String, auth: Option[String]) extends Command with RunContinuationData {
  import io.netty.buffer.Unpooled
  import io.netty.handler.codec.base64.Base64
  import io.netty.util.CharsetUtil
  
  import mx.domain.repo.JdbcMxRepository
  import mx.imap.{ Session, State }
  import mx.imap.response.{ Tagged, Untagged }
  
  import CompletionResult._
  import RunResult._
  
  def run()(implicit session: Session, repo: JdbcMxRepository) = (mechanism, auth) match {
    case (AuthenticateDef.Plain(), None) ⇒
      NeedsContinuationData(this)
    case (AuthenticateDef.Plain(), Some(auth)) ⇒
      authenticatePlain(auth)
    case (_, _) ⇒
      Completed(session, Tagged(tag, Bad("Unsupported AUTHENTICATE mechanism")))
  } 
  
  def runWithContinuationData(continuationData: String)(implicit session: Session, repo: JdbcMxRepository) = authenticatePlain(continuationData)
  
  // echo -en "\0<username>\0<password>" | base64
  private def authenticatePlain(auth: String)(implicit session: Session, repo: JdbcMxRepository) = try {
    Base64.decode(Unpooled.copiedBuffer(auth, CharsetUtil.US_ASCII))
        .toString(CharsetUtil.US_ASCII)
        .split('\0') match {
          case Array(_, username, password) ⇒
            repo.getUserByEmailAndPassword(username, password) match {
              case Some(user) ⇒
                Completed(Session(State.Authenticated, Some(user)), Tagged(tag, Ok(s"${user.email} authenticated")))
              case None ⇒
                Completed(session, Tagged(tag, Bad("Invalid credentials")))
            }
        }
  } catch {
    case _: Throwable ⇒
      Completed(session, Tagged(tag, Bad("Invalid credentials")))
  }
}
