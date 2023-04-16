package com.example.auth

import java.nio.charset.StandardCharsets
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.util.Try

object KeyTools {

  /** Parse the string key into a private key using the given algorithm */
  def parsePKCS8PrivateKey(key: String, keyAlgo: String = "RSA"): Try[PrivateKey] = Try {
    val keySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(parsePEMKey(key))
    KeyFactory.getInstance(keyAlgo).generatePrivate(keySpec)
  }

  private def parsePEMKey(key: String): Array[Byte] = Base64.getDecoder.decode(cleanPEMKeyMarkers(key))

  private def cleanPEMKeyMarkers(key: String): String =
    key
      .replaceAll("-----BEGIN (.*?)-----", "")
      .replaceAll("-----END (.*?)-----", "")
      .replaceAll("\r\n", "")
      .replaceAll("\n", "")
      .trim

  /**
   * Returns true iff the salted SHA256 hash of plainText matches password
   * Both password and salted are assumed to be supplied in Base64 encoded format!
   */
  def verifyHmacHash(plainText: Array[Byte], salt: String, saltedPassword: String): Try[Boolean] = Try {
    saltedPassword == new String(
      Base64.getEncoder.encode(generateHmacHash(plainText, salt)),
      StandardCharsets.UTF_8
    )
  }

  private val HMAC_ALGORITHM_NAME = "HmacSHA256"

  /**
   * Returns the hmac sah256 hash over plainText using salt as its key
   */
  private def generateHmacHash(plainText: Array[Byte], salt: String): Array[Byte] = {
    val sha256_HMAC = Mac.getInstance(HMAC_ALGORITHM_NAME)
    val secret_key  = new SecretKeySpec(Base64.getDecoder.decode(salt), HMAC_ALGORITHM_NAME)
    sha256_HMAC.init(secret_key)
    sha256_HMAC.doFinal(plainText)
  }

}
