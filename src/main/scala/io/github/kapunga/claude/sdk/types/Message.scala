package io.github.kapunga.claude.sdk.types

import io.circe.JsonObject

import io.github.kapunga.claude.sdk.codec.WireEnum

/** Messages exchanged with the Claude Code CLI. */
sealed trait Message

/** User message. */
final case class UserMessage(
  content: Either[String, List[ContentBlock]],
  uuid: Option[String] = None,
  parentToolUseId: Option[String] = None,
  toolUseResult: Option[JsonObject] = None,
) extends Message

/** Error types that can occur on assistant messages. */
enum AssistantMessageError(val wireValue: String) extends WireEnum:
  case AuthenticationFailed extends AssistantMessageError("authentication_failed")
  case BillingError extends AssistantMessageError("billing_error")
  case RateLimit extends AssistantMessageError("rate_limit")
  case InvalidRequest extends AssistantMessageError("invalid_request")
  case ServerError extends AssistantMessageError("server_error")
  case Unknown extends AssistantMessageError("unknown")

object AssistantMessageError
    extends WireEnum.Companion[AssistantMessageError](AssistantMessageError.values)

/** Assistant message with content blocks. */
final case class AssistantMessage(
  content: List[ContentBlock],
  model: String,
  parentToolUseId: Option[String] = None,
  error: Option[AssistantMessageError] = None,
) extends Message

/** System message with metadata. */
final case class SystemMessage(
  subtype: String,
  data: JsonObject,
) extends Message

/** Result message with cost and usage information. */
final case class ResultMessage(
  subtype: String,
  durationMs: Int,
  durationApiMs: Int,
  isError: Boolean,
  numTurns: Int,
  sessionId: String,
  totalCostUsd: Option[Double] = None,
  usage: Option[JsonObject] = None,
  result: Option[String] = None,
  structuredOutput: Option[io.circe.Json] = None,
) extends Message

/** Stream event for partial message updates during streaming. */
final case class StreamEvent(
  uuid: String,
  sessionId: String,
  event: JsonObject,
  parentToolUseId: Option[String] = None,
) extends Message
