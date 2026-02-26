package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.github.kapunga.claude.sdk.types.*

object MessageCodecs:

  import ContentBlockCodecs.given

  // AssistantMessageError encoder/decoder provided by its WireEnum companion — import via types.*

  /** Decode a Message from a JSON object using the "type" discriminator. */
  given Decoder[Message] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "user" =>
        val msgC = c.downField("message").downField("content")
        val contentResult: Decoder.Result[Either[String, List[ContentBlock]]] =
          msgC.as[String].map(Left(_))
            .orElse(msgC.as[List[ContentBlock]].map(Right(_)))
        for
          content         <- contentResult
          uuid            <- c.downField("uuid").as[Option[String]]
          parentToolUseId <- c.downField("parent_tool_use_id").as[Option[String]]
          toolUseResult   <- c.downField("tool_use_result").as[Option[JsonObject]]
        yield UserMessage(content, uuid, parentToolUseId, toolUseResult)

      case "assistant" =>
        for
          content         <- c.downField("message").downField("content").as[List[ContentBlock]]
          model           <- c.downField("message").downField("model").as[String]
          parentToolUseId <- c.downField("parent_tool_use_id").as[Option[String]]
          error           <- c.downField("error").as[Option[AssistantMessageError]]
        yield AssistantMessage(content, model, parentToolUseId, error)

      case "system" =>
        for
          subtype <- c.downField("subtype").as[String]
          data    <- c.as[JsonObject]
        yield SystemMessage(subtype, data)

      case "result" =>
        for
          subtype          <- c.downField("subtype").as[String]
          durationMs       <- c.downField("duration_ms").as[Int]
          durationApiMs    <- c.downField("duration_api_ms").as[Int]
          isError          <- c.downField("is_error").as[Boolean]
          numTurns         <- c.downField("num_turns").as[Int]
          sessionId        <- c.downField("session_id").as[String]
          totalCostUsd     <- c.downField("total_cost_usd").as[Option[Double]]
          usage            <- c.downField("usage").as[Option[JsonObject]]
          result           <- c.downField("result").as[Option[String]]
          structuredOutput <- c.downField("structured_output").as[Option[Json]]
        yield ResultMessage(subtype, durationMs, durationApiMs, isError, numTurns, sessionId,
          totalCostUsd, usage, result, structuredOutput)

      case "stream_event" =>
        for
          uuid            <- c.downField("uuid").as[String]
          sessionId       <- c.downField("session_id").as[String]
          event           <- c.downField("event").as[JsonObject]
          parentToolUseId <- c.downField("parent_tool_use_id").as[Option[String]]
        yield StreamEvent(uuid, sessionId, event, parentToolUseId)

      case other =>
        Left(DecodingFailure(s"Unknown message type: $other", c.history))
    }
  }
