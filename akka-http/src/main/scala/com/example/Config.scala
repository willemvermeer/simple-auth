package com.example

import com.example.auth.KeyTools
import com.typesafe.config.ConfigFactory
import com.zaxxer.hikari.HikariConfig
import scala.jdk.CollectionConverters._

import java.security.PrivateKey

case class SimpleAuthConfig(http: HttpConfig, db: DbConfig, auth: AuthConfig)
object SimpleAuthConfig {
  def fromEnv(key: String, defaultValue: String): String = {
    val envMap  = System.getenv().asScala.toMap
    val fullKey = s"CONFIG_FORCE_${key.replace("-", "_")}"
    envMap.getOrElse(fullKey, defaultValue)
  }
  def load(): SimpleAuthConfig = {
    val cfg   = ConfigFactory.load("simple-token.conf")
    val dbCfg = cfg.getConfig("db")
    val db = DbConfig(
      poolName = dbCfg.getString("pool-name"),
      autoCommit = dbCfg.getBoolean("auto-commit"),
      maximumPoolSize = dbCfg.getInt("maximum-pool-size"),
      user = dbCfg.getString("user"),
      password = dbCfg.getString("password"),
      schema = dbCfg.getString("schema"),
      host = fromEnv("db-host", dbCfg.getString("host")),
      port = fromEnv("db-port", dbCfg.getInt("port").toString).toInt
    )
    val httpCfg = cfg.getConfig("http")
    val http = HttpConfig(
      fromEnv("http-interface", httpCfg.getString("interface")),
      fromEnv("http-port", httpCfg.getInt("port").toString).toInt
    )
    val authCfg    = cfg.getConfig("auth")
    val privateKey = authCfg.getString("private-key")
    val auth = AuthConfig(
      KeyTools.parsePKCS8PrivateKey(privateKey).get,
      authCfg.getString("issuer"),
      authCfg.getString("audience")
    )
    SimpleAuthConfig(db = db, http = http, auth = auth)
  }
}
case class DbConfig(
  poolName: String,
  autoCommit: Boolean,
  maximumPoolSize: Int,
  user: String,
  password: String,
  schema: String,
  host: String,
  port: Int
) {
  def url = s"jdbc:postgresql://$host:$port/$schema"
  def hikari: HikariConfig = {
    val hikonf = new HikariConfig()
    hikonf.setPoolName(poolName)
    hikonf.setMaximumPoolSize(maximumPoolSize)
    hikonf.setAutoCommit(autoCommit)
    hikonf.setReadOnly(false)

    hikonf.setJdbcUrl(url)
    hikonf.setUsername(user)
    hikonf.setPassword(password)

    hikonf.validate()
    hikonf
  }
}

case class HttpConfig(interface: String, port: Int)

case class AuthConfig(privateKey: PrivateKey, issuer: String, audience: String)
