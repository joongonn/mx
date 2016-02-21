package mx.imap.command

object LoginDef extends FixedArgsCommand("LOGIN", 2) {
  override def makeCommand(tag: String, completedArgs: List[String]) = Login(tag, completedArgs(0), completedArgs(1))
}

case class Login(tag: String, username: String, password: String) extends Command {
  import mx.domain.repo.JdbcMxRepository
  import mx.imap.{ Session, State }
  import mx.imap.response.{ Tagged, Untagged }

  import CompletionResult._
  import RunResult._

  def run()(implicit session: Session, repo: JdbcMxRepository) = repo.getUserByEmailAndPassword(username, password) match {
    case Some(user) ⇒
      Completed(Session(State.Authenticated, Some(user)), Tagged(tag, Ok(s"${user.email} authenticated")))
    case None ⇒
      Completed(session, Tagged(tag, No("Invalid credentials")))
  }
}
