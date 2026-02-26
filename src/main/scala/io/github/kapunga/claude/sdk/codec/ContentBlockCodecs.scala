package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*

import io.github.kapunga.claude.sdk.types.*

object ContentBlockCodecs:

  given Encoder[ToolResultContent] = Encoder.instance {
    case ToolResultContent.Text(v) => Json.fromString(v)
    case ToolResultContent.Parts(v) => Json.arr(v.map(Json.fromJsonObject)*)
  }

  given Decoder[ToolResultContent] = Decoder.instance { c =>
    c.as[String]
      .map(ToolResultContent.Text(_))
      .orElse(c.as[List[JsonObject]].map(ToolResultContent.Parts(_)))
  }

  given Encoder[ContentBlock] = Encoder.instance {
    case TextBlock(text) =>
      Json.obj("type" -> "text".asJson, "text" -> text.asJson)
    case ThinkingBlock(thinking, signature) =>
      Json.obj(
        "type" -> "thinking".asJson,
        "thinking" -> thinking.asJson,
        "signature" -> signature.asJson,
      )
    case ToolUseBlock(id, name, input) =>
      Json.obj(
        "type" -> "tool_use".asJson,
        "id" -> id.asJson,
        "name" -> name.asJson,
        "input" -> input.asJson,
      )
    case ToolResultBlock(toolUseId, content, isError) =>
      Json.obj(
        "type" -> "tool_result".asJson,
        "tool_use_id" -> toolUseId.asJson,
        "content" -> content.asJson,
        "is_error" -> isError.asJson,
      )
  }

  given Decoder[ContentBlock] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "text" =>
        c.downField("text").as[String].map(TextBlock(_))
      case "thinking" =>
        for
          thinking <- c.downField("thinking").as[String]
          signature <- c.downField("signature").as[String]
        yield ThinkingBlock(thinking, signature)
      case "tool_use" =>
        for
          id <- c.downField("id").as[String]
          name <- c.downField("name").as[String]
          input <- c.downField("input").as[JsonObject]
        yield ToolUseBlock(id, name, input)
      case "tool_result" =>
        for
          toolUseId <- c.downField("tool_use_id").as[String]
          content <- c.downField("content").as[Option[ToolResultContent]]
          isError <- c.downField("is_error").as[Option[Boolean]]
        yield ToolResultBlock(toolUseId, content, isError)
      case other =>
        Left(DecodingFailure(s"Unknown content block type: $other", c.history))
    }
  }
