package io.github.kapunga.claude.sdk.types

import io.circe.JsonObject

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
enum AssistantMessageError:
  case AuthenticationFailed, BillingError, RateLimit, InvalidRequest, ServerError, Unknown

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
