package com.example.db

import com.zaxxer.hikari.HikariDataSource

import java.sql.Connection
import java.util.UUID
import scala.util.Try

object DbQuery {

  val sql = """select id, name, email, hashpassword, salt from users where email = ?""".stripMargin

  def userInfo(username: String, pool: HikariConnectionPool): Try[UserInfo] =
    pool.connection.flatMap { connection =>
      Try {
        val stmt = connection.prepareStatement(sql)
        stmt.setString(1, username)
        val res = stmt.executeQuery()
        res.next()
        val usr = UserInfo(
          id = UUID.fromString(res.getString(1)),
          name = res.getString(2),
          email = res.getString(3),
          hashpassword = res.getString(4),
          salt = res.getString(5)
        )
        res.close()
        stmt.close()
        connection.close()
        usr
      }
    }

}

trait ConnectionPool {
  def connection: Try[Connection]
  def close: Unit
}

case class HikariConnectionPool private (datasource: HikariDataSource) extends ConnectionPool {
  override def connection: Try[Connection] = Try(datasource.getConnection)

  override def close: Unit = datasource.close
}
