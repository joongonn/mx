package mx.imap.command

object CapabilityDef extends NoArgsCommandDef("CAPABILITY") {
  override def makeCommand(tag: String) = Capability(tag)
}

case class Capability(tag: String) extends Command {
  import mx.domain.repo.JdbcMxRepository
  import mx.imap.Session
  import mx.imap.response.{ Tagged, Untagged }

  import CompletionResult.Ok
  import RunResult.Completed

  def run()(implicit session: Session, repo: JdbcMxRepository) = Completed(session, Untagged("CAPABILITY IMAP4rev1 AUTH=PLAIN") :: Tagged(tag, Ok("CAPABILITY completed")) :: Nil)
}
