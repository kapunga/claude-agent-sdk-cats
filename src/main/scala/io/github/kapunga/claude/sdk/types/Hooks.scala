package io.github.kapunga.claude.sdk.types

import cats.effect.IO
import io.circe.{Json, JsonObject}

/** Hook event types. */
enum HookEvent:
  case PreToolUse, PostToolUse, PostToolUseFailure, UserPromptSubmit
  case Stop, SubagentStop, PreCompact, Notification, SubagentStart
  case PermissionRequest

/** Base fields present across many hook events. */
final case class BaseHookFields(
    sessionId: String,
    transcriptPath: String,
    cwd: String,
    permissionMode: Option[String] = None,
)

/** Strongly-typed hook input variants discriminated by hook event name. */
sealed trait HookInput:
  def base: BaseHookFields

final case class PreToolUseHookInput(
    base: BaseHookFields,
    toolName: String,
    toolInput: JsonObject,
    toolUseId: String,
) extends HookInput

final case class PostToolUseHookInput(
    base: BaseHookFields,
    toolName: String,
    toolInput: JsonObject,
    toolResponse: Json,
    toolUseId: String,
) extends HookInput

final case class PostToolUseFailureHookInput(
    base: BaseHookFields,
    toolName: String,
    toolInput: JsonObject,
    toolUseId: String,
    error: String,
    isInterrupt: Option[Boolean] = None,
) extends HookInput

final case class UserPromptSubmitHookInput(
    base: BaseHookFields,
    prompt: String,
) extends HookInput

final case class StopHookInput(
    base: BaseHookFields,
    stopHookActive: Boolean,
) extends HookInput

final case class SubagentStopHookInput(
    base: BaseHookFields,
    stopHookActive: Boolean,
    agentId: String,
    agentTranscriptPath: String,
    agentType: String,
) extends HookInput

final case class PreCompactHookInput(
    base: BaseHookFields,
    trigger: String,
    customInstructions: Option[String],
) extends HookInput

final case class NotificationHookInput(
    base: BaseHookFields,
    message: String,
    title: Option[String] = None,
    notificationType: String,
) extends HookInput

final case class SubagentStartHookInput(
    base: BaseHookFields,
    agentId: String,
    agentType: String,
) extends HookInput

final case class PermissionRequestHookInput(
    base: BaseHookFields,
    toolName: String,
    toolInput: JsonObject,
    permissionSuggestions: Option[List[Json]] = None,
) extends HookInput

// --- Hook-specific output types ---

/** Hook-specific output for PreToolUse events. */
final case class PreToolUseHookSpecificOutput(
    permissionDecision: Option[String] = None,
    permissionDecisionReason: Option[String] = None,
    updatedInput: Option[JsonObject] = None,
    additionalContext: Option[String] = None,
)

/** Hook-specific output for PostToolUse events. */
final case class PostToolUseHookSpecificOutput(
    additionalContext: Option[String] = None,
    updatedMCPToolOutput: Option[Json] = None,
)

/** Hook-specific output for PostToolUseFailure events. */
final case class PostToolUseFailureHookSpecificOutput(
    additionalContext: Option[String] = None,
)

/** Hook-specific output for UserPromptSubmit events. */
final case class UserPromptSubmitHookSpecificOutput(
    additionalContext: Option[String] = None,
)

/** Hook-specific output for Notification events. */
final case class NotificationHookSpecificOutput(
    additionalContext: Option[String] = None,
)

/** Hook-specific output for SubagentStart events. */
final case class SubagentStartHookSpecificOutput(
    additionalContext: Option[String] = None,
)

/** Hook-specific output for PermissionRequest events. */
final case class PermissionRequestHookSpecificOutput(
    decision: JsonObject,
)

/** Union of hook-specific outputs. */
sealed trait HookSpecificOutput

object HookSpecificOutput:
  final case class PreToolUse(output: PreToolUseHookSpecificOutput) extends HookSpecificOutput
  final case class PostToolUse(output: PostToolUseHookSpecificOutput) extends HookSpecificOutput
  final case class PostToolUseFailure(output: PostToolUseFailureHookSpecificOutput) extends HookSpecificOutput
  final case class UserPromptSubmit(output: UserPromptSubmitHookSpecificOutput) extends HookSpecificOutput
  final case class Notification(output: NotificationHookSpecificOutput) extends HookSpecificOutput
  final case class SubagentStart(output: SubagentStartHookSpecificOutput) extends HookSpecificOutput
  final case class PermissionRequest(output: PermissionRequestHookSpecificOutput) extends HookSpecificOutput

/** Async hook JSON output that defers hook execution. */
final case class AsyncHookJsonOutput(
    asyncTimeout: Option[Int] = None,
)

/** Synchronous hook JSON output with control and decision fields. */
final case class SyncHookJsonOutput(
    continue_ : Option[Boolean] = None,
    suppressOutput: Option[Boolean] = None,
    stopReason: Option[String] = None,
    decision: Option[String] = None,
    systemMessage: Option[String] = None,
    reason: Option[String] = None,
    hookSpecificOutput: Option[HookSpecificOutput] = None,
)

/** Hook JSON output - either async or sync. */
enum HookJsonOutput:
  case Async(output: AsyncHookJsonOutput)
  case Sync(output: SyncHookJsonOutput)

/** Context information for hook callbacks. */
final case class HookContext(
    signal: Option[Nothing] = None, // Reserved for future abort signal support
)

/** Hook callback type. */
type HookCallback = (HookInput, Option[String], HookContext) => IO[HookJsonOutput]

/** Hook matcher configuration. */
final case class HookMatcher(
    matcher: Option[String] = None,
    hooks: List[HookCallback] = Nil,
    timeout: Option[Double] = None,
)
