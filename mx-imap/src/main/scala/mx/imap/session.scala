package mx.imap

import mx.domain.repo.User

sealed abstract class State

object State {
  case object NotAuthenticated extends State
  case object Authenticated extends State
  case object Selected extends State
  case object Logout extends State
}

object Session {
  import State._
  
  def apply() = new Session(NotAuthenticated, None)
}

case class Session(state: State, user: Option[User])
