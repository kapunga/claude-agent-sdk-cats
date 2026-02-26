package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.github.kapunga.claude.sdk.types.*
import munit.CatsEffectSuite

class ContentBlockCodecsSpec extends CatsEffectSuite:

  import ContentBlockCodecs.given

  test("decode TextBlock") {
    val json = parse("""{"type": "text", "text": "Hello world"}""").toOption.get
    val result = json.as[ContentBlock]
    assertEquals(result, Right(TextBlock("Hello world")))
  }

  test("encode TextBlock roundtrip") {
    val block: ContentBlock = TextBlock("Hello world")
    val json = block.asJson
    val decoded = json.as[ContentBlock]
    assertEquals(decoded, Right(block))
  }

  test("decode ThinkingBlock") {
    val json = parse("""{"type": "thinking", "thinking": "Let me think...", "signature": "sig123"}""").toOption.get
    val result = json.as[ContentBlock]
    assertEquals(result, Right(ThinkingBlock("Let me think...", "sig123")))
  }

  test("encode ThinkingBlock roundtrip") {
    val block: ContentBlock = ThinkingBlock("thinking", "sig")
    val json = block.asJson
    val decoded = json.as[ContentBlock]
    assertEquals(decoded, Right(block))
  }

  test("decode ToolUseBlock") {
    val json = parse("""{"type": "tool_use", "id": "tu1", "name": "Bash", "input": {"command": "ls"}}""").toOption.get
    val result = json.as[ContentBlock]
    val expected = ToolUseBlock("tu1", "Bash", JsonObject("command" -> "ls".asJson))
    assertEquals(result, Right(expected))
  }

  test("encode ToolUseBlock roundtrip") {
    val block: ContentBlock = ToolUseBlock("tu1", "Bash", JsonObject("command" -> "ls".asJson))
    val json = block.asJson
    val decoded = json.as[ContentBlock]
    assertEquals(decoded, Right(block))
  }

  test("decode ToolResultBlock with string content") {
    val json = parse("""{"type": "tool_result", "tool_use_id": "tu1", "content": "output text"}""").toOption.get
    val result = json.as[ContentBlock]
    val expected = ToolResultBlock("tu1", Some(ToolResultContent.Text("output text")), None)
    assertEquals(result, Right(expected))
  }

  test("decode ToolResultBlock with null content") {
    val json = parse("""{"type": "tool_result", "tool_use_id": "tu1", "is_error": true}""").toOption.get
    val result = json.as[ContentBlock]
    val expected = ToolResultBlock("tu1", None, Some(true))
    assertEquals(result, Right(expected))
  }

  test("decode unknown block type fails") {
    val json = parse("""{"type": "unknown_block"}""").toOption.get
    val result = json.as[ContentBlock]
    assert(result.isLeft)
  }
