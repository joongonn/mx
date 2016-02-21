package mx.smtp.inbound

object Conversation {
  object States extends Enumeration {
    type State = Value
    val AwaitGreeting, AwaitSenderAddr, AwaitRecipients, AwaitData, AwaitDeliverySystemReply = Value
  }
  
  object CommandMatchers {
    // "HELO" SP Domain
    val EhloCmd = """(?i)EHLO(?:\s+(.*?)\s*)?""".r
    // "HELO" SP Domain
    val HeloCmd = """(?i)HELO(?:\s+(.*?)\s*)?""".r
    // "MAIL FROM:" ("<>" / Reverse-Path) [SP Mail-parameters]
    val MailCmd = """(?i)MAIL(?:\s+FROM\s*:\s*<\s*(.*)\s*>\s*)?|MAIL\s+.*""".r
    // "RCPT TO:" ("<Postmaster@" domain ">" / "<Postmaster>" / Forward-Path) [SP Rcpt-parameters]
    val RcptCmd = """(?i)RCPT(?:\s+TO\s*:\s*<\s*(.*)\s*>\s*)?|RCPT\s+.*""".r
    // "DATA"
    val DataCmd = """(?i)DATA\s*""".r
    // "VRFY" SP String
    val VrfyCmd = """(?i)VRFY\s*""".r
    // "EXPN" SP String
    val ExpnCmd = """(?i)EXPN\s*""".r
    // "RSET"
    val RsetCmd = """(?i)RSET\s*""".r
    // "QUIT"
    val QuitCmd = """(?i)QUIT(?:\s+.*)?""".r
    // "NOOP" [ SP String ]
    val NoopCmd = """(?i)NOOP\s*""".r
    // "HELP" [ SP String ]
    val HelpCmd = """(?i)HELP\s*""".r
    // "STARTTLS"
    val StartTlsCmd = """(?i)STARTTLS\s*""".r
  }
  
  object Replies {
    import mx.smtp.delivery.CheckResult
    
    val Banner = "220 <mydomain> ESMTP"  
    
    val Ok = "250 Ok"
    
    val HeloOk = (domain: String) ⇒ s"250 OK ${domain}"
    val HeloSyntax = "501 Syntax: HELO hostname"
    val HeloFirst = "503 Send HELO first"
    
    val EhloSyntax = "501 Syntax: EHLO hostname"
    val EhloOk = Seq("250-<mydomain> ESMTP", "250 STARTTLS")

    val MailSyntax = "501 Syntax: MAIL FROM:<address>"
    val MailOk = "250 Mail OK"
    val MailFail = (reason: CheckResult) ⇒ s"450 Mail Failed: ${reason.toString}"
    val MailFirst = "503 Error: need MAIL command first"
    val MailSenderRejected = "553 Sender rejected"

    val RcptOk = "250 Rcpt OK"
    val RcptFail = (reason: CheckResult) ⇒ s"554 Rcpt FAIL: ${reason.toString}"
    val RcptFirst = "503 Error: need RCPT first"
    val RcptSyntax = "501 Syntax: RCPT TO:<address>"

    val DataOk = "354 End data with <CR><LF>.<CR><LF>"
    val DataFail = (reason: CheckResult) ⇒ s"554 Mail data FAIL: ${reason.toString}"
    val DataQueuedForDelivery = "250 OK, queued for delivery"
    
    val RsetOk = "250 OK resetted"

    val Bye = "221 Bye"
    
    val Help = "214 No help available"
    
    val ReadyToStartTls = "220 Reeeady to start TLS"
    val TlsAlreadyStarted = "554 Error: TLS already started"
    
    val Unrecognized = "502 Command not recognized"
    val NotImplemented = "502 Command not implemented"
    val TimedOut = "421 <mydomain> Idle timeout exceeded"
    
    val TransactionFailed = "554 Transaction failed (internal server error), please try again later"
  }
}