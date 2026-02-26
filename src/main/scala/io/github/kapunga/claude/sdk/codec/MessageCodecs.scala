package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.derivation.{Configuration, ConfiguredDecoder}

import io.github.kapunga.claude.sdk.types.*

object MessageCodecs:

  import ContentBlockCodecs.given

  // AssistantMessageError encoder/decoder provided by its WireEnum companion — import via types.*

  private given Configuration = Configuration.default.withSnakeCaseMemberNames
  private given Decoder[ResultMessage] = ConfiguredDecoder.derived
  private given Decoder[StreamEvent] = ConfiguredDecoder.derived

  /** Decode a Message from a JSON object using the "type" discriminator. */
  given Decoder[Message] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "user" =>
        val msgC = c.downField("message").downField("content")
        val contentResult: Decoder.Result[Either[String, List[ContentBlock]]] =
          msgC
            .as[String]
            .map(Left(_))
            .orElse(msgC.as[List[ContentBlock]].map(Right(_)))
        for
          content <- contentResult
          uuid <- c.downField("uuid").as[Option[String]]
          parentToolUseId <- c.downField("parent_tool_use_id").as[Option[String]]
          toolUseResult <- c.downField("tool_use_result").as[Option[JsonObject]]
        yield UserMessage(content, uuid, parentToolUseId, toolUseResult)

      case "assistant" =>
        for
          content <- c.downField("message").downField("content").as[List[ContentBlock]]
          model <- c.downField("message").downField("model").as[String]
          parentToolUseId <- c.downField("parent_tool_use_id").as[Option[String]]
          error <- c.downField("error").as[Option[AssistantMessageError]]
        yield AssistantMessage(content, model, parentToolUseId, error)

      case "system" =>
        for
          subtype <- c.downField("subtype").as[String]
          data <- c.as[JsonObject]
        yield SystemMessage(subtype, data)

      case "result" => c.as[ResultMessage]

      case "stream_event" => c.as[StreamEvent]

      case other =>
        Left(DecodingFailure(s"Unknown message type: $other", c.history))
    }
  }
