package mx.imap.response

import mx.imap.command.CompletionResult

sealed abstract class Response
case class Untagged(line: String) extends Response
case class Tagged(tag: String, result: CompletionResult) extends Response
