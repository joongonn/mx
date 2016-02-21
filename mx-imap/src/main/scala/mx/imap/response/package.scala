package mx.imap

import mx.imap.parse.ParseError
import mx.imap.parse.ParseError._

package object response {
  
  implicit def responseToList(response: Response) = response :: Nil
  
}
