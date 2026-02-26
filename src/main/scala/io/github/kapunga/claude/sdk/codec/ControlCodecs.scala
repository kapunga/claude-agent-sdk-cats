package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*

import io.github.kapunga.claude.sdk.types.*

object ControlCodecs:

  /** Decode an SDKControlRequest from a JSON object. */
  given Decoder[SDKControlRequest] = Decoder.instance { c =>
    for
      requestId <- c.downField("request_id").as[String]
      reqC = c.downField("request")
      subtype <- reqC.downField("subtype").as[String]
      request <- subtype match
        case "interrupt" =>
          Right(SDKControlInterruptRequest())
        case "can_use_tool" =>
          for
            toolName <- reqC.downField("tool_name").as[String]
            input <- reqC.downField("input").as[JsonObject]
            permissionSuggestions <- reqC.downField("permission_suggestions").as[Option[List[Json]]]
            blockedPath <- reqC.downField("blocked_path").as[Option[String]]
          yield SDKControlPermissionRequest(toolName, input, permissionSuggestions, blockedPath)
        case "initialize" =>
          for
            hooks <- reqC.downField("hooks").as[Option[JsonObject]]
            agents <- reqC.downField("agents").as[Option[JsonObject]]
          yield SDKControlInitializeRequest(hooks, agents)
        case "set_permission_mode" =>
          reqC.downField("mode").as[PermissionMode].map(SDKControlSetPermissionModeRequest(_))
        case "hook_callback" =>
          for
            callbackId <- reqC.downField("callback_id").as[String]
            input <- reqC.downField("input").as[Json]
            toolUseId <- reqC.downField("tool_use_id").as[Option[String]]
          yield SDKHookCallbackRequest(callbackId, input, toolUseId)
        case "mcp_message" =>
          for
            serverName <- reqC.downField("server_name").as[String]
            message <- reqC.downField("message").as[JsonObject]
          yield SDKControlMcpMessageRequest(serverName, message)
        case "rewind_files" =>
          reqC.downField("user_message_id").as[String].map(SDKControlRewindFilesRequest(_))
        case other =>
          Left(DecodingFailure(s"Unknown control request subtype: $other", reqC.history))
    yield SDKControlRequest(requestId, request)
  }

  /** Encode an SDKControlResponse to JSON. */
  given Encoder[SDKControlResponse] = Encoder.instance { resp =>
    val inner = resp.response match
      case ControlResponseData.Success(r) =>
        Json.obj(
          "subtype" -> "success".asJson,
          "request_id" -> r.requestId.asJson,
          "response" -> r.response.asJson,
        )
      case ControlResponseData.Error(r) =>
        Json.obj(
          "subtype" -> "error".asJson,
          "request_id" -> r.requestId.asJson,
          "error" -> r.error.asJson,
        )
    Json.obj("type" -> "control_response".asJson, "response" -> inner)
  }

  /** Encode a control request to send to the CLI. */
  def encodeControlRequest(requestId: String, request: JsonObject): Json =
    Json.obj(
      "type" -> "control_request".asJson,
      "request_id" -> requestId.asJson,
      "request" -> Json.fromJsonObject(request),
    )

  /** Decode a control response from the CLI. */
  given Decoder[ControlResponseData] = Decoder.instance { c =>
    c.downField("subtype").as[String].flatMap {
      case "success" =>
        for
          requestId <- c.downField("request_id").as[String]
          response <- c.downField("response").as[Option[JsonObject]]
        yield ControlResponseData.Success(ControlSuccessResponse(requestId, response))
      case "error" =>
        for
          requestId <- c.downField("request_id").as[String]
          error <- c.downField("error").as[String]
        yield ControlResponseData.Error(ControlErrorResponse(requestId, error))
      case other =>
        Left(DecodingFailure(s"Unknown control response subtype: $other", c.history))
    }
  }
