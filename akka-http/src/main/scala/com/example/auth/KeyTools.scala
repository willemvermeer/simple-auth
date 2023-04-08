package com.example.auth

import java.security.{ KeyFactory, PrivateKey, PublicKey }
import java.security.spec.{ PKCS8EncodedKeySpec, X509EncodedKeySpec }
import java.util.Base64
import scala.util.Try

object KeyTools {

  private def parsePEMKey(key: String): Array[Byte] = Base64.getDecoder.decode(cleanPEMKeyMarkers(key))

  def cleanPEMKeyMarkers(key: String): String =
    key
      .replaceAll("-----BEGIN (.*?)-----", "")
      .replaceAll("-----END (.*?)-----", "")
      .replaceAll("\r\n", "")
      .replaceAll("\n", "")
      .trim

  /** Parse the string key into a public key using the given algorithm */
  def parseX509EPublicKey(key: String, keyAlgo: String = "RSA"): Try[PublicKey] = Try {
    val spec = new X509EncodedKeySpec(parsePEMKey(key))
    KeyFactory.getInstance(keyAlgo).generatePublic(spec)
  }

  /** Parse the string key into a private key using the given algorithm */
  def parsePKCS8PrivateKey(key: String, keyAlgo: String = "RSA"): Try[PrivateKey] = Try {
    val keySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(parsePEMKey(key))
    KeyFactory.getInstance(keyAlgo).generatePrivate(keySpec)
  }

}
