package mx.imap.parse

import org.junit.runner._

import org.specs2.mutable._
import org.specs2.runner.JUnitRunner

/*
    C: A001 LOGIN {11}
    S: + Ready for additional command text
    C: FRED FOOBAR {7}
    S: + Ready for additional command text
    C: fat man
    S: A001 OK LOGIN completed
    C: A044 BLURDYBLOOP {102856}
    S: A044 BAD No such command as "BLURDYBLOOP"
*/

@RunWith(classOf[JUnitRunner])
class ClientInputParserSpec extends Specification {
  import ClientInputParser._
  
   "ClientInputParser" should {
     "parse command 'a01 Login test@mail.com pass'" in {
       val tagPartResult = ClientInputParser.parse(tagParser, "a01 Login test@mail.com pass")
       tagPartResult.get mustEqual "a01"
       val cmdPartResult = ClientInputParser.parse(commandParser, tagPartResult.next)
       //cmdPartResult.get mustEqual CommandPart("Login", Some(List(StringParam("test@mail.com"), StringParam("pass"))))
       cmdPartResult.get mustEqual "LOGIN"
     }
     
     "parse command 'a01 Login'" in {
       val tagPartResult = ClientInputParser.parse(tagParser, "a01 Login")
       val cmdPartResult = ClientInputParser.parse(commandParser, tagPartResult.next)
       //cmdPartResult.get mustEqual CommandPart("Login", None)
       cmdPartResult.get mustEqual "LOGIN"
     }

     "parse command 'a01 Login {11}'" in {
       val tagPartResult = ClientInputParser.parse(tagParser, "a01 Login {11}")
       val cmdPartResult = ClientInputParser.parse(commandParser, tagPartResult.next)
       // cmdPartResult.get mustEqual CommandPart("Login", Some(List(LiteralParam(11))))
       cmdPartResult.get mustEqual "LOGIN"
     }
     
     "not parse command 'a01 Login {11}xyz'" in {
       val tagPartResult = ClientInputParser.parse(tagParser, "a01 Login {11}xyz")
       val cmdPartResult = ClientInputParser.parse(commandParser, tagPartResult.next)
       cmdPartResult.successful mustEqual true
     }
   }
}