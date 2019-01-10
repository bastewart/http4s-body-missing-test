package com.permutive

import cats.syntax.all._
import cats.effect._
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.jetty.JettyClient
import org.http4s.client.middleware.Logger
import org.http4s.client.okhttp.OkHttpBuilder

import scala.concurrent.ExecutionContext.global

object Clients {

  private[this] def addLogger[F[_] : Concurrent](client: Client[F]): Client[F] =
    Logger(logHeaders = true, logBody = true)(client)

  private[this] def createHttp4sClient[F[_] : Effect](client: Client[F]): Resource[F, Http4sClient[F]] =
    Resource.liftF(Http4sClient(client))

  private[this] def setupClient[F[_] : ConcurrentEffect](description: String)(client: Client[F]): Resource[F, (Http4sClient[F], String)] =
    createHttp4sClient(addLogger(client))
      .map(c => (c, description))

  def async[F[_] : ConcurrentEffect]: Resource[F, (Http4sClient[F], String)] =
    AsyncHttpClient.resource[F]()
      .flatMap(setupClient("Async"))

  def blaze[F[_]: ConcurrentEffect]: Resource[F, (Http4sClient[F], String)] =
    BlazeClientBuilder(global).resource
      .flatMap(setupClient("Blaze"))

  def jetty[F[_] : ConcurrentEffect]: Resource[F, (Http4sClient[F], String)] =
    JettyClient.resource()
      .flatMap(setupClient("Jetty"))

  def okHttp[F[_] : ConcurrentEffect : ContextShift]: Resource[F, (Http4sClient[F], String)] =
    OkHttpBuilder.withDefaultClient(global)
      .flatMap(_.resource)
      .flatMap(setupClient("OkHttp"))

}
