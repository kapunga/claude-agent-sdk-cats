package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.github.kapunga.claude.sdk.types.*
import munit.CatsEffectSuite

class MessageCodecsSpec extends CatsEffectSuite:

  import MessageCodecs.given

  test("decode UserMessage with string content") {
    val json = parse("""{
      "type": "user",
      "message": {"content": "Hello"},
      "uuid": "abc-123",
      "parent_tool_use_id": null,
      "tool_use_result": null
    }""").toOption.get
    val result = json.as[Message]
    assert(result.isRight)
    result.foreach {
      case msg: UserMessage =>
        assertEquals(msg.content, Left("Hello"))
        assertEquals(msg.uuid, Some("abc-123"))
      case other => fail(s"Expected UserMessage, got $other")
    }
  }

  test("decode AssistantMessage") {
    val json = parse("""{
      "type": "assistant",
      "message": {
        "content": [
          {"type": "text", "text": "The answer is 4."}
        ],
        "model": "claude-sonnet-4-5-20250929"
      },
      "parent_tool_use_id": null
    }""").toOption.get
    val result = json.as[Message]
    assert(result.isRight)
    result.foreach {
      case msg: AssistantMessage =>
        assertEquals(msg.model, "claude-sonnet-4-5-20250929")
        assertEquals(msg.content.length, 1)
        assert(msg.content.head.isInstanceOf[TextBlock])
      case other => fail(s"Expected AssistantMessage, got $other")
    }
  }

  test("decode AssistantMessage with error") {
    val json = parse("""{
      "type": "assistant",
      "message": {
        "content": [],
        "model": "claude-sonnet-4-5-20250929"
      },
      "error": "rate_limit"
    }""").toOption.get
    val result = json.as[Message]
    assert(result.isRight)
    result.foreach {
      case msg: AssistantMessage =>
        assertEquals(msg.error, Some(AssistantMessageError.RateLimit))
      case other => fail(s"Expected AssistantMessage, got $other")
    }
  }

  test("decode SystemMessage") {
    val json = parse("""{
      "type": "system",
      "subtype": "init",
      "data": {"key": "value"}
    }""").toOption.get
    val result = json.as[Message]
    assert(result.isRight)
    result.foreach {
      case msg: SystemMessage =>
        assertEquals(msg.subtype, "init")
      case other => fail(s"Expected SystemMessage, got $other")
    }
  }

  test("decode ResultMessage") {
    val json = parse("""{
      "type": "result",
      "subtype": "success",
      "duration_ms": 1234,
      "duration_api_ms": 1000,
      "is_error": false,
      "num_turns": 3,
      "session_id": "sess-123",
      "total_cost_usd": 0.05,
      "result": "Done"
    }""").toOption.get
    val result = json.as[Message]
    assert(result.isRight)
    result.foreach {
      case msg: ResultMessage =>
        assertEquals(msg.durationMs, 1234)
        assertEquals(msg.isError, false)
        assertEquals(msg.numTurns, 3)
        assertEquals(msg.sessionId, "sess-123")
        assertEquals(msg.totalCostUsd, Some(0.05))
        assertEquals(msg.result, Some("Done"))
      case other => fail(s"Expected ResultMessage, got $other")
    }
  }

  test("decode StreamEvent") {
    val json = parse("""{
      "type": "stream_event",
      "uuid": "uuid-1",
      "session_id": "sess-1",
      "event": {"type": "content_block_delta", "delta": {"type": "text_delta", "text": "Hi"}}
    }""").toOption.get
    val result = json.as[Message]
    assert(result.isRight)
    result.foreach {
      case msg: StreamEvent =>
        assertEquals(msg.uuid, "uuid-1")
        assertEquals(msg.sessionId, "sess-1")
      case other => fail(s"Expected StreamEvent, got $other")
    }
  }

  test("decode unknown message type fails") {
    val json = parse("""{"type": "future_type", "data": {}}""").toOption.get
    val result = json.as[Message]
    assert(result.isLeft)
  }
