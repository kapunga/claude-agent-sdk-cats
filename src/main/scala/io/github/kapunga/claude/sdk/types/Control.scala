package io.github.kapunga.claude.sdk.types

import io.circe.{Json, JsonObject}

// --- Control requests FROM the CLI ---

/** SDK control request subtypes from the CLI. */
sealed trait SDKControlRequestData

case object SDKControlInterruptRequest extends SDKControlRequestData

final case class SDKControlPermissionRequest(
  toolName: String,
  input: JsonObject,
  permissionSuggestions: Option[List[Json]],
  blockedPath: Option[String],
) extends SDKControlRequestData

final case class SDKControlInitializeRequest(
  hooks: Option[JsonObject],
  agents: Option[JsonObject],
) extends SDKControlRequestData

final case class SDKControlSetPermissionModeRequest(
  mode: PermissionMode
) extends SDKControlRequestData

final case class SDKHookCallbackRequest(
  callbackId: String,
  input: Json,
  toolUseId: Option[String],
) extends SDKControlRequestData

final case class SDKControlMcpMessageRequest(
  serverName: String,
  message: JsonObject,
) extends SDKControlRequestData

final case class SDKControlRewindFilesRequest(
  userMessageId: String
) extends SDKControlRequestData

/** Wrapper for a control request from the CLI. */
final case class SDKControlRequest(
  requestId: String,
  request: SDKControlRequestData,
)

// --- Control responses TO the CLI ---

/** Successful control response. */
final case class ControlSuccessResponse(
  requestId: String,
  response: Option[JsonObject],
)

/** Error control response. */
final case class ControlErrorResponse(
  requestId: String,
  error: String,
)

/** Control response variants. */
enum ControlResponseData:
  case Success(response: ControlSuccessResponse)
  case Error(response: ControlErrorResponse)

/** Wrapper for a control response to the CLI. */
final case class SDKControlResponse(
  response: ControlResponseData
)
