package com.permutive

import cats.effect.Effect
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Json
import org.http4s.circe.jsonEncoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.{EntityBody, EntityEncoder, Headers, MediaType, Method, Request, Uri}

class Http4sClient[F[_]](client: Client[F])(implicit F: Effect[F]) {

  private[this] implicit val logger: Logger[F] = Slf4jLogger.unsafeCreate[F]
  private[this] object dsl extends Http4sDsl[F] with Http4sClientDsl[F]

  def requestManual(uri: Uri, body: Json): F[String] = {
    val bodyString = body.noSpaces
    for {
      hBody    <- http4sBody(bodyString)
      request  =  Request(method = Method.POST, uri = uri, headers = Headers(`Content-Type`(MediaType.application.json)), body = hBody)
      _        <- Logger[F].info(s"Sending manual request: `$request`; with body: `$bodyString`")
      response <- client.expect[String](request)
    } yield response
  }

  private[this] def http4sBody(body: String)(implicit encoder: EntityEncoder[F, String]): F[EntityBody[F]] =
    F.pure(encoder.toEntity(body).body)

  def requestDsl(uri: Uri, body: Json): F[String] = {
    import dsl._

    client.expect[String](POST(body, uri))
  }

}



object Http4sClient {

  def apply[F[_] : Effect](client: Client[F]): F[Http4sClient[F]] =
    Effect[F].delay(new Http4sClient(client))

}
