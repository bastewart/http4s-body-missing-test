package com.permutive

import cats.effect._
import cats.syntax.all._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax._
import org.http4s.Uri
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {

  val Port = 8080
  val RequestUri = Uri.unsafeFromString(s"http://127.0.0.1:$Port")
  val CorrectTestBody = TestBodyApi("bar")
  val CorrectTestJson = CorrectTestBody.asJson

  def testRequest[F[_] : Sync](sendF: (Uri, Json) => F[String]): F[Either[Throwable, Boolean]] = {
    // Retrieve a basic string from the server, ensures there is a body in the request
    // Then decode result to check nothing has changed
    sendF(RequestUri, CorrectTestJson)
      .attempt
      .map { _.flatMap(decode[TestBodyApi]).map(_ == CorrectTestBody) }
  }

  def testClient[F[_] : Sync : Logger](clientDescription: (Http4sClient[F], String)): F[Unit] = {
    val (client: Http4sClient[F], desc: String) = clientDescription

    for {
      _         <- Logger[F].info(s"Starting test of $desc")
      _         <- Logger[F].info(s"Sending manual request using $desc")
      resManual <- testRequest(client.requestManual)
      _         <- Logger[F].info(s"Result for manual request using $desc: $resManual")
      _         <- Logger[F].info(s"Sending DSL request using $desc")
      resDsl    <- testRequest(client.requestDsl)
      passed    =  testPassed(resManual) && testPassed(resDsl)
      _         <- Logger[F].info(s"Result for DSL request using $desc: $resDsl")
      _         <- Logger[F].info(s"Finished test of $desc. Passed: $passed")
    } yield ()
  }

  def testPassed(res: Either[Throwable, Boolean]): Boolean =
    res.fold(_ => false, _ == true)

  def testAll[F[_] : ConcurrentEffect : ContextShift : Logger]: F[Unit] = {
    for {
      _ <- Clients.async.use(testClient[F])
      _ <- Clients.blaze.use(testClient[F])
      _ <- Clients.jetty.use(testClient[F])
      _ <- Clients.okHttp.use(testClient[F])
    } yield ()
  }

  // Has a single route: POST to the root which sends the JSON sent straight back
  def server[F[_] : ConcurrentEffect : Timer]: Resource[F, Server[F]] = {
    val routes = new Routes[F]

    BlazeServerBuilder[F]
      .bindHttp(Port).withHttpApp(routes.postMirror)
      .resource
  }

  override def run(args: List[String]): IO[ExitCode] = {
    implicit val logger: Logger[IO] = Slf4jLogger.unsafeCreate[IO]

    server[IO].use { _ =>
      testAll[IO].as(ExitCode.Success)
    }
  }

}
