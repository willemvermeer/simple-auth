package com.example.auth
import com.example.AuthConfig
import org.bouncycastle.util.encoders.UTF8
import pdi.jwt.{ Jwt, JwtAlgorithm, JwtBase64, JwtClaim, JwtHeader }
import sun.nio.cs.UTF_8

import java.nio.charset.Charset
import java.security.{ MessageDigest, PrivateKey }
import java.time.Clock
import scala.util.Try

object TestApp extends App {

  val algorithm     = JwtAlgorithm.fromString("RS256")
  val defaultHeader = JwtHeader(algorithm = Some(algorithm))
  val hasher        = Try(MessageDigest.getInstance("SHA-256"))

  def createFieldHash(data: String, algorithm: JwtAlgorithm): Try[String] =
    hasher.flatMap { hasher =>
      Try {
        val encodedToken      = JwtBase64.encodeString(data)
        val reencodedToken    = JwtBase64.encode(encodedToken)
        val hash: Array[Byte] = hasher.digest(reencodedToken)
        val halfHash          = hash.take(hash.size / 2)
        JwtBase64.encodeString(halfHash)
      }
    }

  val res = createFieldHash("hello internet!", algorithm)
  println(res)
  // aGVsbG8gaW50ZXJuZXQh
  // aGVsbG8gaW50ZXJuZXQh
//  HHEUoulC6B0oTKBoByTgGQ
//  HHEUoulC6B0oTKBoByTgGQ

  def claimWithDefaults(issuer: String, audience: String, claim: JwtClaim = JwtClaim()): JwtClaim = {
    implicit val clock = Clock.systemUTC()
    claim
      .by(issuer)
      .to(audience)
      .issuedNow
      .expiresIn(10 * 60)
  }

  def encode(header: JwtHeader, claims: JwtClaim, privateKey: PrivateKey): Try[String] = Try {
    Jwt.encode(header, claims, privateKey)
  }

  def createAccessToken(pk: PrivateKey, content: AccessClaims, coreClaims: JwtClaim = JwtClaim()) = {

    import org.json4s.{ DefaultFormats, Formats }
    import org.json4s.ext.JavaTypesSerializers
    import org.json4s.native.Serialization._
    implicit val formats: Formats = DefaultFormats ++ JavaTypesSerializers.all

    for {
      extraClaimsJson <- Try(write(content))
      _               = println(s"extraClaimsJson = $extraClaimsJson")
      claims          = claimWithDefaults("my_issuer", "my_audience", coreClaims.withContent(extraClaimsJson))
      _               = println(s"claims = $claims")
      _               = println(s"claims = ${claims.toJson}")
      header          = defaultHeader
      rawToken        <- encode(header, claims, pk)
    } yield AccessToken(header, claims, content, rawToken)

  }

  val privateKeyStr =
    "-----BEGIN PRIVATE KEY-----MIIJQgIBADANBgkqhkiG9w0BAQEFAASCCSwwggkoAgEAAoICAQC4vpAinkNpyXHlhNztH5dZYJNZueK9J1hiFZkgGWoOiVNMEVON3jZRjlv7IbTSUFCgyISt1BM9t9aI02qYCVfi4wwr/QctZHr4Wjjt90xAI3TsNFWpsp1z0ZNc9Om6/o2uFIKGRASfUKAgiOA3n+XYHy31M1JENelAAuGkYpqJGs5F9GZmO5/LW351iv6WlxQlA/6dt/ErlBZkXHqSWvzMyD2vbvSpBrYY9VuNlnYQoBCEu3Nj+Dom9oCqq2Py6VsWXRPnwY5N94LJR5rf/MRTLlWwZaJERT217wzcnHwpeReXMt5MnUiHtYdgPzjZj2Uvw4KlWC08f8eyoyRZXqFykbRvxkWxsfYxE9BCXHF0dPX4/GBoSKdg+FZ5kiJt5wDXzatMJLOWBqp8QFJBKAcQKdGPfFC5As/gOgys0UFyK4ITRaNsF+bFqx03fPicO+kgdKWKUfEO/kPStSA8HfLK9UiKGRjz+kUHKQEgKfz6kjiw4hg6IseVo1trQDE+WFSbXHRUw3jHoRDcTEYMKQNjOm6bUMNhxoW8df4SfpOhgYXZkXIfOt6toMyjaLOhoG4OmRD+KEq+D5oNYTJt0RDDV1XIeJfKKeaFTE5GWXE3m67KZiIvbpmETEqXc1YeW5JeR23r50JTbLbntZOPIagBpxuvpfdunkBMWiwHenwBZQIDAQABAoICAQClFoUuqkaLjdwdwfC6ZPSWhdqm35lidnpgi/Rd3tgLTWQGIaWMrPnVP2WqsKApIGQsiYMm4tCe4lzvwB0M56yFr2b9GAsF4TiSHe2pmMemuQiO2uWB038oNCt/ccw7saVh3ioVWUrYIvA0opvamSSHULV82/OzyIk7Dlgc0ZBO3IlfsdqfmMBetM/4sdB3OWJjAYLR5cG5s4ee/5T4nB4z0OxujLRNJxYqj3bUu5Odn6ujMO8YcoVks/qeiLG0LBudjGdxPmGwAnH48+fOKM43lrwz+V5bq5RNs+TGb3/0f6U5E9faluJtgyYKN/TRVXaSjB9/KaCi43zMgUTGJ+4UMdlx6K6jMrGcAvVyMy5TaNQKHcz+c4hfxFrSwEg6OxIvR2NwLslBLRCtr/kAYc7zu4U+xUKLpzOfnki3Z1p2XKRC/gk/IJbxTiYIcSc6kCKZKI4olo/DhYQsXX+B4ZQ0Z0FpddVKXLwn3OkWBELDcd66umVnyz0O6KjmnWV3Xqs6MKCHBm7+cRQ21TYuN0Rhe4MAoMrxbYB5ICoPsdr9BIO8zVjTcCUxH/7WXS/KfR7fHh1ts/wAnO4lctcGSDBhK8FUXwk8Imsu76VyvXTjBqIA6QY+XiwF2xk6yw3tC4SZBX/j4VXqLP65JFoXU1W2d5F8RZOULgCqdQhwc9JJgQKCAQEA6KDTFWnNJVuHSEYd6bvl+R8fOhhWH3DzczMklwLsGFEat/uN/BqBnqTLrjK7ObS0dfpfnT6BzBeSqkjRXjSKqhAWL1VEiq+4+KP8UxfECPMZyA5BZ1uHBqgG6ZjepY1c6SiNYpdW/41cNH5nCNuFyELAQEyK03MMoC+q1obbYIVb0jWO+0JQPuz38p9UQSpu74N2DuMGB4b6fvo/WdUS5X4CE38sGlo4RlE6pPRFws+yhT6efG9vOMmhSIgdVIxxrpp+8+x8oKrgCq86JJ+BPw+eQmhh2gkWFS/gUZ+1Br7Qt3HDAp4hRSony9NsRuoKF1ueZ0CfW8oZjIWzh0FTXQKCAQEAy04rvrjn0CnI6ojO9eFl4w51Pwq7YUH0HhY0W4pmdrMM7re8Qq31ByEByVRYIAR0hUX698Wb+RaEoaah1ec7259G2M8aM9JqECT1eKr04KLKh2unhnro3UKCOkw3MMkRrPr3zYopnvq4gF7CyvXib3up6suoYgkcxZbMgJ+K3lb/xbfA+mvYO7a3Ey6CsSj7AeYBGVSJlxXy8CEc7mOIygNlEICcZX5ZvT0DDa7Iy05CWjtoY+XZUUAkVT/OtxAfzvF0oo/NaAyWS05uRI61CPfk39WtUYQEFZJklUTK5GPEoaiTIKR+y/Dym0fSgQVwPQc7i68yJg/oiVcPr91NqQKCAQBmH+xrEyaXhuYOCZIqQW4Ffu8zjumIJgsKgOJIVWUWi8yCBrJfgbdz0P8F+4mMHNO4k3EUVBOhaJvfm9YmWESjwiorH3DWvawjo8IrLFzIXQjtdayq1lihyHQ4av8biMLILSBcrvnneMSI2xEap1zE/ODvmWq8x148mJCUW2HFruYPk6mwjn0yEtALOH1BmoJXLcxPXsUP2ubvhab4Jf2EuFvq+UKAUykvXYu0TPSvQIUrz8C9+iHGuWXPtMnDi2CA+ZxQM3rHAh0XM7P/zfJsn8undbG3iZOjO4Br9QQA8RYp4oxtAsSyW35bEfcP7sD9XiBdTdZn8oJi2XNmJnyVAoIBADYbjeTqjTeHh2N0GbCy1k0Bsg6fKpON3AyN2E1sniyFJrH50G7moUnObQ1KF1IeHWzC3TJIM2Stq4riMDwfmHyw/UgChnS4UWYOkA4XYajPaptz+Vf/Ki6I3uPpGN8xBDVHbeAUH1OiYqw3jBw8KJGHFgfZP+0ng7LKmY6551mZSeUzLHb5cMkjrcG7JSlFQNiWgEBfkWbWfpuOFIcVknhBQwqLUBRq9jM5I7DmjaYeI+aj3PqJV7caHRFbNAbgkbaf6GzDXOWORAmyzAIPAPtsDkvMMJ57RiL/nSlk76KtMs3bcZzEasKYU9kj2lBgRCjZ3e0lKXGb1kWHGDOz8FECggEAbRCSFZWKhRZI6EHAOWPZXHouL2t+WAnexylKEJCZq+eDWrHV4SsSInBTke5MkCyV4B0rlqh7XtTgF+t/HmuW976VsOSG1SavfWykupdh8i2OnVUGZ42ihIzmoGo3HAt23cB/tTwkaKAtSudIPo4knw11Isc/E4yug8DD3b14OBI/UFs1jdz7pjAANbm+0rFx4+wumyZIIZeFJVp2nxz4IsIVCBWVaJMiHIS7xBKIjL62IoYRM4U3Qov5G11hDM7Jlp6F863iL/E7h6VC5RiFW9mHwzzyaRFzZlxNOGuQoTQuDniTilGSYILk293agFiNJCYlkEeve7d+DqspEzRZBg==-----END PRIVATE KEY-----"
  val privateKey = KeyTools.parsePKCS8PrivateKey(privateKeyStr).get

  val conf = AuthConfig(privateKey, "my_issuer", "my_audience")
  val at   = createAccessToken(privateKey, AccessClaims("my_claim")).get
  println(at.rawToken)

  // eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.IntcImlzc1wiOlwibXlfaXNzdWVyXCIsXCJhdWRcIjpcIm15X2F1ZGllbmNlXCIsXCJzZXNzaW9uX2lkXCI6XCJzb21lX2lkXCJ9Ig.A2q2jxSrT1Wm0Txbtzpv_jx5r7e5earrQdXeD25rO3ap_OX3XnKGbc4P4Vsi5XDlVuW2y7F196XJdAfugRVVeeQCZI5f_4iYtC3HmTkKH9p74jZnrQ5Ry1v4aZnQ4urplT-mQ0xAe1U3oxomrLc4JKeQXQscxYiSc7sac2r0UzuQsCvORoJySAan6K5UDe3ISXTTnPZw274q9pGuXjz6tgvE9Iy36lQh0hMfJiMobidz0X2Y0M1Zou2kJgC-5n2jZetoWeXXPEcTTgjCDj_Gmfb4d7bkeoShj0YSEs0FfzweQueTW0UDC-gPTTkfmJfD_Xdw9h4ZyqD62x49U31TKQ
  // eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJteV9pc3N1ZXIiLCJhdWQiOiJteV9hdWRpZW5jZSIsImV4cCI6MTY3OTczOTg3MiwiaWF0IjoxNjc5NzM5MjcyLCJzZXNzaW9uSWQiOiJteV9jbGFpbSJ9.VPGV1hZiIDDqo0ajn0wAoMxWd-otM_h6pYVMk4Foo-xP3BYLvvZmvP5USdJAm7gbvXbjtHCvpSj3_mYIKLaLpAUj1IISo4Mp5nSWW3KiT7Vz--2CsQ4RW9rV0aVlR-0SIIfefs8GcmtnDXAJnXG8nNmzucs1pK4LtTKiBjAojbSOvUWH_xqgIKUn7KzoS_X45NTK7p_joMdYy0RBLqBJHBDve3gaJ_HoQHkJva6qv3Ftk8DV38O44ucs0RYusUph9oO5pQ7k8lKjOU25laJuAonOEGyNUhStOVPHB9FKNvUF-M9EEeAt7ntksp1xDYM75L3EPGZE5mpFjYW7y23r8mjylml_LjLUoP-CJuLmgbjqDxzGV0jPjeLoNr4cHkR9fBmTHPwUuS-M7k8owFWKjLor5JpiHuJRIKO3FaDRhL8cGvpzj4pSHo0PhH1-kdf9gLj7gy5rss8eS5jnyo_fJnN6Z-5_UMoGayKWWeT0LQrUXWcj1HLFP2ZFVpzV5ayUq9iZbfJZVkRJ5ZXRjII3ygAEkjPJZQ76iFPkVQRem4gBRWSt9F4E_K9UI-WHFFT1Mdtnd63anCiXZyf9zCILQGaUL9klfoH6T0RVvHNTIoVU_0I479A6oa5uXHdMplldh0qcepm-zcTl2VCDE15ltvtBj1jd9H7VxidbrHv-Vd0

}
