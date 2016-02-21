package mx.domain

import java.util.Date

case class EmailAddress(address: String, personal: Option[String])
case class Email(subject: String, from: EmailAddress, to: Option[List[EmailAddress]], availbleFmts: Map[String, String], attachments: List[EmailAttachment], sent: Option[Date])
case class EmailAttachment(contentType: String, name: String, size: Int)
