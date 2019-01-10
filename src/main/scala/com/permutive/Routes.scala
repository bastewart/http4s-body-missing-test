package com.permutive

import cats.effect._
import cats.syntax.all._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.middleware.Logger

class Routes[F[_] : Concurrent] extends Http4sDsl[F] {

  // Simply send whatever json was received straight back
  val postMirror = Logger(logHeaders = true, logBody = true)(
    HttpRoutes.of[F] {
      case req @ POST -> Root =>
        for {
          body <- req.as[Json]
          resp <- Ok(body)
        } yield resp
    }.orNotFound
  )

}
