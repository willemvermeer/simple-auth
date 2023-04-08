package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.Base64
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class CryptoSpec extends AnyWordSpec with Matchers {
  def generateHmacHashBase64(plainText: Array[Byte], salt: String): String =
    new String(Base64.getEncoder.encode(generateHmacHash(plainText, salt)), StandardCharsets.UTF_8)

  def generateHmacHash(plainText: Array[Byte], salt: String): Array[Byte] = {
    val sha256_HMAC = Mac.getInstance("HmacSHA256")
    val secret_key  = new SecretKeySpec(Base64.getDecoder.decode(salt), "HmacSHA256")
    sha256_HMAC.init(secret_key)
    sha256_HMAC.doFinal(plainText)
  }

  def generateSalt: String = {
    val uuid = UUID.randomUUID()
    val bb = ByteBuffer
      .allocate(16)
      .putLong(uuid.getLeastSignificantBits)
      .putLong(uuid.getMostSignificantBits)
    new String(Base64.getEncoder.encode(bb.array))
  }

  def verifyHmacHash(plainText: Array[Byte], salt: String, password: String): Boolean =
    password == new String(
      Base64.getEncoder.encode(generateHmacHash(plainText, salt)),
      StandardCharsets.UTF_8
    )

  "create salt" should {
    "create salt" in {
      val salt           = generateSalt
      val password       = "TopSecret0!"
      val saltedPassword = generateHmacHashBase64(password.getBytes(StandardCharsets.UTF_8), salt)
      println(salt)
      println(saltedPassword)
      verifyHmacHash(password.getBytes(StandardCharsets.UTF_8), salt, saltedPassword) shouldBe true
    }
  }
}
