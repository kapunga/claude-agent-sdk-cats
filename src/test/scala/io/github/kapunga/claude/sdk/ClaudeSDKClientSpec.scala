package io.github.kapunga.claude.sdk

import cats.effect.{IO, Ref}
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.transport.Transport
import io.github.kapunga.claude.sdk.types.*
import munit.CatsEffectSuite

class ClaudeSDKClientSpec extends CatsEffectSuite:

  // Basic type tests to verify the public API compiles correctly
  test("ClaudeAgentOptions has sensible defaults") {
    val opts = ClaudeAgentOptions()
    assertEquals(opts.allowedTools, Nil)
    assertEquals(opts.disallowedTools, Nil)
    assertEquals(opts.continueConversation, false)
    assertEquals(opts.includePartialMessages, false)
    assertEquals(opts.forkSession, false)
    assertEquals(opts.enableFileCheckpointing, false)
    assertEquals(opts.env, Map.empty[String, String])
    assertEquals(opts.betas, Nil)
  }

  test("ClaudeAgentOptions can be customized") {
    val opts = ClaudeAgentOptions(
      model = Some("claude-sonnet-4-5-20250929"),
      permissionMode = Some(PermissionMode.AcceptEdits),
      maxTurns = Some(10),
      maxBudgetUsd = Some(1.0),
      systemPrompt = Some(SystemPrompt.Text("Be helpful")),
    )
    assertEquals(opts.model, Some("claude-sonnet-4-5-20250929"))
    assertEquals(opts.permissionMode, Some(PermissionMode.AcceptEdits))
    assertEquals(opts.maxTurns, Some(10))
  }

  test("PermissionResult ADT works correctly") {
    val allow: PermissionResult = PermissionResult.Allow(PermissionResultAllow())
    val deny: PermissionResult = PermissionResult.Deny(PermissionResultDeny("not allowed", true))

    allow match
      case PermissionResult.Allow(r) => assert(r.updatedInput.isEmpty)
      case _                         => fail("Expected Allow")

    deny match
      case PermissionResult.Deny(r) =>
        assertEquals(r.message, "not allowed")
        assertEquals(r.interrupt, true)
      case _ => fail("Expected Deny")
  }

  test("Message ADT type checking") {
    val user: Message = UserMessage(Left("Hello"))
    val assistant: Message = AssistantMessage(List(TextBlock("Hi")), "test-model")
    val system: Message = SystemMessage("init", JsonObject.empty)
    val result: Message = ResultMessage("success", 100, 80, false, 1, "sess-1")

    assert(user.isInstanceOf[UserMessage])
    assert(assistant.isInstanceOf[AssistantMessage])
    assert(system.isInstanceOf[SystemMessage])
    assert(result.isInstanceOf[ResultMessage])
  }
