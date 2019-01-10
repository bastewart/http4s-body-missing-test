Test if the body of a `POST` request "goes missing" when constructing a manual `Request` (using the `apply` method of 
`Request`); it also sees if the client `RequestLogger` logs the request correctly.

It repeats the test using the `http4s` DSL to construct the request.

`sbt run` should test all client implementations (`Async`, `Blaze`, `Jetty`, `OkHttp`).

Currently:
 * "Manual" does not work for `Async` or `OkHttp` in addition the `RequestLogger` does not log
 * Sending using the DSL works for all implementations and the logging is successful _except_ for `OkHttp`
   - If the client logger is disabled then it is successful for `OkHttp`
 
Uses a local `http4s` server as the endpoint for requests, this simply sends whatever JSON it received straight back.

A "manual" request looks like this:

```scala
def requestManual(uri: Uri, body: Json): F[String] = {
  val client: Client[F] = ???

  for {
    hBody    <- http4sBody(body.noSpaces)
    request  =  Request(method = Method.POST, uri = uri, headers = Headers(`Content-Type`(MediaType.application.json)), body = hBody)
    response <- client.expect[String](request)
  } yield response
}

def http4sBody(body: String)(implicit encoder: EntityEncoder[F, String]): F[EntityBody[F]] =
  F.pure(encoder.toEntity(body).body)  // I have tried this as F.delay and it does not affect the result
```

A request using the DSL looks like this:

```scala
def requestDsl(uri: Uri, body: Json): F[String] = {
  val client: Client[F] = ???

  client.expect[String](POST(body, uri))
}
```

### `OkHttp` client request logger

With `OkHttp` using the DSL the following error results when the client request logger is enabled (from googling
the error is because the response body is being read twice):

```scala
[info] [scala-execution-context-global-10] WARN  o.h.c.o.OkHttpBuilder - Unable to write to OkHttp sink
[info] java.lang.IllegalStateException: closed
[info]  at okio.RealBufferedSink.write(RealBufferedSink.java:84)
[info]  at org.http4s.client.okhttp.OkHttpBuilder$$anon$4.$anonfun$writeTo$3(OkHttpBuilder.scala:137)
[info]  at scala.runtime.java8.JFunction0$mcV$sp.apply(JFunction0$mcV$sp.java:23)
[info]  at cats.effect.internals.IORunLoop$.cats$effect$internals$IORunLoop$$loop(IORunLoop.scala:87)
[info]  at cats.effect.internals.IORunLoop$RestartCallback.signal(IORunLoop.scala:351)
[info]  at cats.effect.internals.IORunLoop$RestartCallback.apply(IORunLoop.scala:372)
[info]  at cats.effect.internals.IORunLoop$RestartCallback.apply(IORunLoop.scala:312)
[info]  at cats.effect.internals.IOShift$Tick.run(IOShift.scala:36)
[info]  at java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1402)
[info]  at java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:289)
[info]  at java.util.concurrent.ForkJoinPool$WorkQueue.runTask(ForkJoinPool.java:1056)
[info]  at java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1692)
[info]  at java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:157)
```

Disabling the client fixes this and the whole request / response is successful when using the DSL.


