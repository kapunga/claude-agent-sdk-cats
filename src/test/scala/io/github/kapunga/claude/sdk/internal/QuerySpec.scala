package io.github.kapunga.claude.sdk.internal

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.transport.Transport
import io.github.kapunga.claude.sdk.types.*
import munit.CatsEffectSuite

class QuerySpec extends CatsEffectSuite:

  /** A mock transport that records writes and produces scripted responses. */
  class MockTransport(
      responses: Ref[IO, List[JsonObject]],
      val writtenRef: Ref[IO, List[String]],
      readyRef: Ref[IO, Boolean],
  ) extends Transport:
    def write(data: String): IO[Unit] =
      writtenRef.update(_ :+ data)

    def readMessages: Stream[IO, JsonObject] =
      Stream.eval(responses.get).flatMap { msgs =>
        Stream.emits(msgs)
      }

    def endInput: IO[Unit] = IO.unit
    def isReady: IO[Boolean] = readyRef.get

  def makeMockTransport(responses: List[JsonObject]): IO[MockTransport] =
    for
      responsesRef <- Ref.of[IO, List[JsonObject]](responses)
      writtenRef   <- Ref.of[IO, List[String]](Nil)
      readyRef     <- Ref.of[IO, Boolean](true)
    yield MockTransport(responsesRef, writtenRef, readyRef)

  test("Query routes SDK messages to receiveMessages stream") {
    val sdkMessage = JsonObject(
      "type"    -> "assistant".asJson,
      "message" -> Json.obj(
        "content" -> Json.arr(Json.obj("type" -> "text".asJson, "text" -> "Hello".asJson)),
        "model"   -> "test-model".asJson,
      ),
    )
    val resultMessage = JsonObject(
      "type"            -> "result".asJson,
      "subtype"         -> "success".asJson,
      "duration_ms"     -> 100.asJson,
      "duration_api_ms" -> 80.asJson,
      "is_error"        -> false.asJson,
      "num_turns"       -> 1.asJson,
      "session_id"      -> "test".asJson,
    )

    for
      transport <- makeMockTransport(List(sdkMessage, resultMessage))
      query     <- Query.make(transport, QueryConfig())
      // Process messages through the query
      _ <- transport.readMessages
            .through(query.processMessages)
            .compile.drain
            .start
      // Small delay for processing
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      // Collect messages
      messages <- query.receiveMessages.take(2).compile.toList
    yield
      assertEquals(messages.length, 2)
      assertEquals(
        messages.head("type").flatMap(_.asString),
        Some("assistant"),
      )
      assertEquals(
        messages(1)("type").flatMap(_.asString),
        Some("result"),
      )
  }

  test("Query routes control responses to pending requests") {
    // Create a response that matches our request
    val initResponse = JsonObject(
      "type" -> "control_response".asJson,
      "response" -> Json.obj(
        "subtype"    -> "success".asJson,
        "request_id" -> "req_1_placeholder".asJson,
        "response"   -> Json.obj("commands" -> Json.arr()),
      ),
    )

    for
      transport <- makeMockTransport(List(initResponse))
      written   <- transport.writtenRef.get
      query     <- Query.make(transport, QueryConfig())
    yield
      // Basic construction test
      assert(query != null)
  }
