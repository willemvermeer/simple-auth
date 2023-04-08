package com.example.ops

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

object FutureTryOps {
  implicit class FutureTryOps[T](val attempt: Try[T]) {
    def toFuture(implicit ec: ExecutionContext): Future[T] = Future(Future.fromTry(attempt)).flatten
  }
}
