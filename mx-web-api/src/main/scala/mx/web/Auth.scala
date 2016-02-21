package mx.web

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}

import sun.misc.{BASE64Decoder, BASE64Encoder}

case class AuthToken(token: String, userId: Long, email: String, expireAt: Long)

object AuthTokens {
//  try { 
//    val field = Class.forName("javax.crypto.JceSecurity").getDeclaredField("isRestricted")
//    field.setAccessible(true)
//    field.set(null, java.lang.Boolean.FALSE) 
//  } catch  {
//    case e: Throwable ⇒
//  }

  sealed trait TokenError
  case object ExpiredToken extends TokenError
  case object InvalidToken extends TokenError
  
  private val Rand = new SecureRandom()
    
  def apply() = new AuthTokens("1234567812345678")
}

class AuthTokens(secretKey: String) {
  import AuthTokens._

  private val secretKeySpec = new SecretKeySpec(secretKey.getBytes, "AES")
  
  def generate(userId: Long, email: String, expireAt: Long) = {
    val b64Encoder = new BASE64Encoder
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val ivBytes = new Array[Byte](16);
    Rand.nextBytes(ivBytes)
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new IvParameterSpec(ivBytes))

    val plainToken = s"${userId}:${email}:${expireAt}"
    val encryptedTokenPart = b64Encoder.encode(cipher.doFinal(plainToken.getBytes))
    val ivPart = b64Encoder.encode(ivBytes)
    
    s"${ivPart}:${encryptedTokenPart}"
  }
  
  def crack(token: String): Either[TokenError, AuthToken] = {
    val b64Decoder = new BASE64Decoder
    
    token.split(":") match {
      case Array(ivPart, encryptedTokenPart) ⇒
        try {
          val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
          val ivBytes = b64Decoder.decodeBuffer(ivPart)
          cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(ivBytes))
          val plainToken = new String(cipher.doFinal(b64Decoder.decodeBuffer(encryptedTokenPart)))
          plainToken.split(":") match {
            case Array(userId, email, expireAt) ⇒
              val expireAtMs = expireAt.toLong
              if (System.currentTimeMillis > expireAtMs) Left(ExpiredToken) else Right(AuthToken(token, userId.toLong, email, expireAtMs)) 
          }
        } catch {
          case _: Throwable ⇒ Left(InvalidToken)
        }
      case _ ⇒ Left(InvalidToken)
    }
  }
}