package io.github.kapunga.claude.sdk.types

import io.circe.JsonObject

/** Content blocks that appear in messages. */
sealed trait ContentBlock

/** Text content block. */
final case class TextBlock(text: String) extends ContentBlock

/** Thinking content block with cryptographic signature. */
final case class ThinkingBlock(thinking: String, signature: String) extends ContentBlock

/** Tool use content block representing a tool invocation. */
final case class ToolUseBlock(id: String, name: String, input: JsonObject) extends ContentBlock

/** Tool result content block representing the output of a tool invocation. */
final case class ToolResultBlock(
  toolUseId: String,
  content: Option[ToolResultContent] = None,
  isError: Option[Boolean] = None,
) extends ContentBlock

/** Content of a tool result - either a simple string or structured content parts. */
enum ToolResultContent:
  case Text(value: String)
  case Parts(value: List[JsonObject])
