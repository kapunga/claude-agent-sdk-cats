package io.github.kapunga.claude.sdk.internal

import io.circe.*
import io.circe.syntax.*

import munit.CatsEffectSuite

import io.github.kapunga.claude.sdk.errors.MessageParseError
import io.github.kapunga.claude.sdk.types.*

class MessageParserSpec extends CatsEffectSuite:

  test("parse user message with string content") {
    val data = JsonObject(
      "type" -> "user".asJson,
      "message" -> Json.obj("content" -> "Hello".asJson),
      "uuid" -> "abc".asJson,
      "parent_tool_use_id" -> Json.Null,
      "tool_use_result" -> Json.Null,
    )
    MessageParser.parse(data).map {
      case Some(msg: UserMessage) =>
        assertEquals(msg.content, Left("Hello"))
        assertEquals(msg.uuid, Some("abc"))
      case other => fail(s"Expected Some(UserMessage), got $other")
    }
  }

  test("parse assistant message") {
    val data = JsonObject(
      "type" -> "assistant".asJson,
      "message" -> Json.obj(
        "content" -> Json.arr(Json.obj("type" -> "text".asJson, "text" -> "Hi".asJson)),
        "model" -> "claude-sonnet-4-5-20250929".asJson,
      ),
      "parent_tool_use_id" -> Json.Null,
    )
    MessageParser.parse(data).map {
      case Some(msg: AssistantMessage) =>
        assertEquals(msg.content, List(TextBlock("Hi")))
        assertEquals(msg.model, "claude-sonnet-4-5-20250929")
      case other => fail(s"Expected Some(AssistantMessage), got $other")
    }
  }

  test("parse result message") {
    val data = JsonObject(
      "type" -> "result".asJson,
      "subtype" -> "success".asJson,
      "duration_ms" -> 100.asJson,
      "duration_api_ms" -> 80.asJson,
      "is_error" -> false.asJson,
      "num_turns" -> 1.asJson,
      "session_id" -> "sess-1".asJson,
      "total_cost_usd" -> 0.01.asJson,
    )
    MessageParser.parse(data).map {
      case Some(msg: ResultMessage) =>
        assertEquals(msg.durationMs, 100)
        assertEquals(msg.sessionId, "sess-1")
      case other => fail(s"Expected Some(ResultMessage), got $other")
    }
  }

  test("parse unknown message type returns None") {
    val data = JsonObject(
      "type" -> "future_feature".asJson,
      "data" -> Json.obj(),
    )
    MessageParser.parse(data).map { result =>
      assertEquals(result, None)
    }
  }

  test("parse message missing type field raises error") {
    val data = JsonObject("data" -> Json.obj())
    MessageParser.parse(data).attempt.map { result =>
      assert(result.isLeft)
      assert(result.left.exists(_.isInstanceOf[MessageParseError]))
    }
  }

  test("parse malformed known message type raises error") {
    val data = JsonObject(
      "type" -> "result".asJson
      // Missing required fields
    )
    MessageParser.parse(data).attempt.map { result =>
      assert(result.isLeft)
      assert(result.left.exists(_.isInstanceOf[MessageParseError]))
    }
  }
