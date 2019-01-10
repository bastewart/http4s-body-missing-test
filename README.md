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

## Log output / results

The whole log output:

```
[info] INFO  c.p.Main - Starting test of Async
[info] INFO  c.p.Main - Sending manual request using Async
[info] INFO  c.p.Http4sClient - Sending manual request: `Request(method=POST, uri=http://127.0.0.1:8080, headers=Headers(Content-Type: application/json))`; with body: `{"foo":"bar"}`
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Content-Type: application/json, Accept: text/*, content-length: 0, host: 127.0.0.1:8080, user-agent: AHC/2.1) body=""
[info] INFO  o.h.s.m.ResponseLogger - service raised an error: class org.http4s.MalformedMessageBodyFailure
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 400 Bad Request Headers(Content-Type: text/plain; charset=UTF-8, Date: Thu, 10 Jan 2019 15:55:15 GMT, Content-Length: 31) body=""
[info] INFO  c.p.Main - Result for manual request using Async: Left(org.http4s.client.UnexpectedStatus: unexpected HTTP status: 400 Bad Request)
[info] INFO  c.p.Main - Sending DSL request using Async
[info] INFO  o.h.c.m.RequestLogger - HTTP/1.1 POST http://127.0.0.1:8080 Headers(Content-Type: application/json, Content-Length: 13, Accept: text/*) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Content-Type: application/json, Content-Length: 13, Accept: text/*, host: 127.0.0.1:8080, user-agent: AHC/2.1) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Date: Thu, 10 Jan 2019 15:55:15 GMT, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  c.p.Main - Result for DSL request using Async: Right(true)
[info] INFO  c.p.Main - Finished test of Async. Passed: false
[info] INFO  c.p.Main - Starting test of Blaze
[info] INFO  c.p.Main - Sending manual request using Blaze
[info] INFO  c.p.Http4sClient - Sending manual request: `Request(method=POST, uri=http://127.0.0.1:8080, headers=Headers(Content-Type: application/json))`; with body: `{"foo":"bar"}`
[info] INFO  o.h.c.m.RequestLogger - HTTP/1.1 POST http://127.0.0.1:8080 Headers(Content-Type: application/json, Accept: text/*) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Host: 127.0.0.1:8080, Content-Type: application/json, Accept: text/*, User-Agent: http4s-blaze/0.20.0-M4, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Date: Thu, 10 Jan 2019 15:55:15 GMT, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  c.p.Main - Result for manual request using Blaze: Right(true)
[info] INFO  c.p.Main - Sending DSL request using Blaze
[info] INFO  o.h.c.m.RequestLogger - HTTP/1.1 POST http://127.0.0.1:8080 Headers(Content-Type: application/json, Content-Length: 13, Accept: text/*) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Host: 127.0.0.1:8080, Content-Type: application/json, Accept: text/*, User-Agent: http4s-blaze/0.20.0-M4, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Date: Thu, 10 Jan 2019 15:55:15 GMT, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  c.p.Main - Result for DSL request using Blaze: Right(true)
[info] INFO  c.p.Main - Finished test of Blaze. Passed: true
[info] INFO  o.h.c.PoolManager - Shutting down connection pool: curAllocated=1 idleQueues.size=1 waitQueue.size=0 maxWaitQueueLimit=256 closed=false
[info] INFO  o.e.j.util.log - Logging initialized @2692ms to org.eclipse.jetty.util.log.Slf4jLog
[info] INFO  c.p.Main - Starting test of Jetty
[info] INFO  c.p.Main - Sending manual request using Jetty
[info] INFO  c.p.Http4sClient - Sending manual request: `Request(method=POST, uri=http://127.0.0.1:8080, headers=Headers(Content-Type: application/json))`; with body: `{"foo":"bar"}`
[info] INFO  o.h.c.m.RequestLogger - HTTP/1.1 POST http://127.0.0.1:8080 Headers(Content-Type: application/json, Accept: text/*) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Accept-Encoding: gzip, User-Agent: Jetty/9.4.14.v20181114, Content-Type: application/json, Accept: text/*, Host: 127.0.0.1:8080, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Date: Thu, 10 Jan 2019 15:55:15 GMT, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  c.p.Main - Result for manual request using Jetty: Right(true)
[info] INFO  c.p.Main - Sending DSL request using Jetty
[info] INFO  o.h.c.m.RequestLogger - HTTP/1.1 POST http://127.0.0.1:8080 Headers(Content-Type: application/json, Content-Length: 13, Accept: text/*) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Accept-Encoding: gzip, User-Agent: Jetty/9.4.14.v20181114, Content-Type: application/json, Accept: text/*, Host: 127.0.0.1:8080, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.s.m.Logger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 200 OK Headers(Content-Type: application/json, Date: Thu, 10 Jan 2019 15:55:15 GMT, Content-Length: 13) body="{"foo":"bar"}"
[info] INFO  c.p.Main - Result for DSL request using Jetty: Right(true)
[info] INFO  c.p.Main - Finished test of Jetty. Passed: true
[info] INFO  c.p.Main - Starting test of OkHttp
[info] INFO  c.p.Main - Sending manual request using OkHttp
[info] INFO  c.p.Http4sClient - Sending manual request: `Request(method=POST, uri=http://127.0.0.1:8080, headers=Headers(Content-Type: application/json))`; with body: `{"foo":"bar"}`
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Content-Type: application/json, Accept: text/*, Transfer-Encoding: chunked, Host: 127.0.0.1:8080, Connection: Keep-Alive, Accept-Encoding: gzip, User-Agent: okhttp/3.12.0) body=""
[info] INFO  o.h.s.m.ResponseLogger - service raised an error: class org.http4s.MalformedMessageBodyFailure
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 400 Bad Request Headers(Content-Length: 31, Content-Type: text/plain; charset=UTF-8, Date: Thu, 10 Jan 2019 15:55:16 GMT) body=""
[info] INFO  c.p.Main - Result for manual request using OkHttp: Left(org.http4s.client.UnexpectedStatus: unexpected HTTP status: 400 Bad Request)
[info] INFO  c.p.Main - Sending DSL request using OkHttp
[info] INFO  o.h.s.m.Logger - HTTP/1.1 POST / Headers(Content-Type: application/json, Accept: text/*, Transfer-Encoding: chunked, Host: 127.0.0.1:8080, Connection: Keep-Alive, Accept-Encoding: gzip, User-Agent: okhttp/3.12.0) body=""
[info] INFO  o.h.s.m.ResponseLogger - service raised an error: class org.http4s.MalformedMessageBodyFailure
[info] INFO  o.h.c.m.RequestLogger - HTTP/1.1 POST http://127.0.0.1:8080 Headers(Content-Type: application/json, Content-Length: 13, Accept: text/*) body="{"foo":"bar"}"
[info] INFO  o.h.c.m.ResponseLogger - HTTP/1.1 400 Bad Request Headers(Content-Length: 31, Content-Type: text/plain; charset=UTF-8, Date: Thu, 10 Jan 2019 15:55:16 GMT) body=""
[info] INFO  c.p.Main - Result for DSL request using OkHttp: Left(org.http4s.client.UnexpectedStatus: unexpected HTTP status: 400 Bad Request)
[info] INFO  c.p.Main - Finished test of OkHttp. Passed: false
[info] WARN  o.h.c.o.OkHttpBuilder - Unable to write to OkHttp sink
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
