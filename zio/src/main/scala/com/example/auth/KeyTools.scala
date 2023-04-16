package com.example.auth

import java.security.spec.PKCS8EncodedKeySpec
import java.security.{ KeyFactory, PrivateKey }
import java.util.Base64
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

}
