package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*

import io.github.kapunga.claude.sdk.types.*

object HookCodecs:

  // HookEvent encoder/decoder provided by its WireEnum companion — import via types.*

  private def decodeBase(c: HCursor): Decoder.Result[BaseHookFields] =
    for
      sessionId <- c.downField("session_id").as[String]
      transcriptPath <- c.downField("transcript_path").as[String]
      cwd <- c.downField("cwd").as[String]
      permissionMode <- c.downField("permission_mode").as[Option[PermissionMode]]
    yield BaseHookFields(sessionId, transcriptPath, cwd, permissionMode)

  given Decoder[HookInput] = Decoder.instance { c =>
    c.downField("hook_event_name").as[String].flatMap {
      case "PreToolUse" =>
        for
          base <- decodeBase(c)
          toolName <- c.downField("tool_name").as[String]
          toolInput <- c.downField("tool_input").as[JsonObject]
          toolUseId <- c.downField("tool_use_id").as[String]
        yield PreToolUseHookInput(base, toolName, toolInput, toolUseId)

      case "PostToolUse" =>
        for
          base <- decodeBase(c)
          toolName <- c.downField("tool_name").as[String]
          toolInput <- c.downField("tool_input").as[JsonObject]
          toolResponse <- c.downField("tool_response").as[Json]
          toolUseId <- c.downField("tool_use_id").as[String]
        yield PostToolUseHookInput(base, toolName, toolInput, toolResponse, toolUseId)

      case "PostToolUseFailure" =>
        for
          base <- decodeBase(c)
          toolName <- c.downField("tool_name").as[String]
          toolInput <- c.downField("tool_input").as[JsonObject]
          toolUseId <- c.downField("tool_use_id").as[String]
          error <- c.downField("error").as[String]
          isInterrupt <- c.downField("is_interrupt").as[Option[Boolean]]
        yield PostToolUseFailureHookInput(base, toolName, toolInput, toolUseId, error, isInterrupt)

      case "UserPromptSubmit" =>
        for
          base <- decodeBase(c)
          prompt <- c.downField("prompt").as[String]
        yield UserPromptSubmitHookInput(base, prompt)

      case "Stop" =>
        for
          base <- decodeBase(c)
          stopHookActive <- c.downField("stop_hook_active").as[Boolean]
        yield StopHookInput(base, stopHookActive)

      case "SubagentStop" =>
        for
          base <- decodeBase(c)
          stopHookActive <- c.downField("stop_hook_active").as[Boolean]
          agentId <- c.downField("agent_id").as[String]
          agentTranscriptPath <- c.downField("agent_transcript_path").as[String]
          agentType <- c.downField("agent_type").as[String]
        yield SubagentStopHookInput(base, stopHookActive, agentId, agentTranscriptPath, agentType)

      case "PreCompact" =>
        for
          base <- decodeBase(c)
          trigger <- c.downField("trigger").as[String]
          customInstructions <- c.downField("custom_instructions").as[Option[String]]
        yield PreCompactHookInput(base, trigger, customInstructions)

      case "Notification" =>
        for
          base <- decodeBase(c)
          message <- c.downField("message").as[String]
          title <- c.downField("title").as[Option[String]]
          notificationType <- c.downField("notification_type").as[String]
        yield NotificationHookInput(base, message, title, notificationType)

      case "SubagentStart" =>
        for
          base <- decodeBase(c)
          agentId <- c.downField("agent_id").as[String]
          agentType <- c.downField("agent_type").as[String]
        yield SubagentStartHookInput(base, agentId, agentType)

      case "PermissionRequest" =>
        for
          base <- decodeBase(c)
          toolName <- c.downField("tool_name").as[String]
          toolInput <- c.downField("tool_input").as[JsonObject]
          permissionSuggestions <- c.downField("permission_suggestions").as[Option[List[Json]]]
        yield PermissionRequestHookInput(base, toolName, toolInput, permissionSuggestions)

      case other =>
        Left(DecodingFailure(s"Unknown hook event name: $other", c.history))
    }
  }

  /**
   * Encode a HookJsonOutput for sending to the CLI.
   * Note: continue_ maps to "continue", matching the CLI protocol.
   */
  given Encoder[HookJsonOutput] = Encoder.instance {
    case HookJsonOutput.Async(output) =>
      Json
        .obj(
          "async" -> Json.True,
          "asyncTimeout" -> output.asyncTimeout.asJson,
        )
        .dropNullValues
    case HookJsonOutput.Sync(output) =>
      Json
        .obj(
          "continue" -> output.continue_.asJson,
          "suppressOutput" -> output.suppressOutput.asJson,
          "stopReason" -> output.stopReason.asJson,
          "decision" -> output.decision.asJson,
          "systemMessage" -> output.systemMessage.asJson,
          "reason" -> output.reason.asJson,
          "hookSpecificOutput" -> output.hookSpecificOutput.map(encodeHookSpecificOutput).asJson,
        )
        .dropNullValues
  }

  private def encodeHookSpecificOutput(hso: HookSpecificOutput): Json = hso match
    case HookSpecificOutput.PreToolUse(o) =>
      Json
        .obj(
          "hookEventName" -> HookEvent.PreToolUse.wireValue.asJson,
          "permissionDecision" -> o.permissionDecision.map(_.wireValue).asJson,
          "permissionDecisionReason" -> o.permissionDecisionReason.asJson,
          "updatedInput" -> o.updatedInput.asJson,
          "additionalContext" -> o.additionalContext.asJson,
        )
        .dropNullValues

    case HookSpecificOutput.PostToolUse(o) =>
      Json
        .obj(
          "hookEventName" -> HookEvent.PostToolUse.wireValue.asJson,
          "additionalContext" -> o.additionalContext.asJson,
          "updatedMCPToolOutput" -> o.updatedMCPToolOutput.asJson,
        )
        .dropNullValues

    case HookSpecificOutput.PostToolUseFailure(o) =>
      Json
        .obj(
          "hookEventName" -> HookEvent.PostToolUseFailure.wireValue.asJson,
          "additionalContext" -> o.additionalContext.asJson,
        )
        .dropNullValues

    case HookSpecificOutput.UserPromptSubmit(o) =>
      Json
        .obj(
          "hookEventName" -> HookEvent.UserPromptSubmit.wireValue.asJson,
          "additionalContext" -> o.additionalContext.asJson,
        )
        .dropNullValues

    case HookSpecificOutput.Notification(o) =>
      Json
        .obj(
          "hookEventName" -> HookEvent.Notification.wireValue.asJson,
          "additionalContext" -> o.additionalContext.asJson,
        )
        .dropNullValues

    case HookSpecificOutput.SubagentStart(o) =>
      Json
        .obj(
          "hookEventName" -> HookEvent.SubagentStart.wireValue.asJson,
          "additionalContext" -> o.additionalContext.asJson,
        )
        .dropNullValues

    case HookSpecificOutput.PermissionRequest(o) =>
      Json.obj(
        "hookEventName" -> HookEvent.PermissionRequest.wireValue.asJson,
        "decision" -> o.decision.asJson,
      )
