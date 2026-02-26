package io.github.kapunga.claude.sdk

import io.github.kapunga.claude.sdk.types.*
import munit.CatsEffectSuite

class TypesSpec extends CatsEffectSuite:

  test("ContentBlock sealed trait covers all variants") {
    val blocks: List[ContentBlock] = List(
      TextBlock("hello"),
      ThinkingBlock("thinking", "sig"),
      ToolUseBlock("id", "name", io.circe.JsonObject.empty),
      ToolResultBlock("id"),
    )
    assertEquals(blocks.length, 4)
  }

  test("HookEvent enum has all expected values") {
    val events = HookEvent.values
    assertEquals(events.length, 10)
    assert(events.contains(HookEvent.PreToolUse))
    assert(events.contains(HookEvent.PostToolUse))
    assert(events.contains(HookEvent.PermissionRequest))
  }

  test("PermissionMode enum has all expected values") {
    val modes = PermissionMode.values
    assertEquals(modes.length, 4)
  }

  test("AssistantMessageError enum has all expected values") {
    val errors = AssistantMessageError.values
    assertEquals(errors.length, 6)
  }

  test("ThinkingConfig variants") {
    val adaptive: ThinkingConfig = ThinkingConfig.Adaptive
    val enabled: ThinkingConfig = ThinkingConfig.Enabled(32000)
    val disabled: ThinkingConfig = ThinkingConfig.Disabled

    enabled match
      case ThinkingConfig.Enabled(budget) => assertEquals(budget, 32000)
      case _ => fail("Expected Enabled")
  }

  test("Effort enum has all expected values") {
    val efforts = Effort.values
    assertEquals(efforts.length, 4)
  }

  test("SdkBeta enum") {
    assertEquals(SdkBeta.Context1m.value, "context-1m-2025-08-07")
  }

  test("McpServerConfig sealed trait covers all variants") {
    val configs: List[McpServerConfig] = List(
      McpStdioServerConfig("node", Some(List("server.js"))),
      McpSSEServerConfig("http://localhost:3000"),
      McpHttpServerConfig("http://localhost:3001"),
    )
    assertEquals(configs.length, 3)
  }

  test("AgentDefinition creation") {
    val agent = AgentDefinition(
      description = "Test agent",
      prompt = "You are a test agent",
      tools = Some(List("Bash", "Read")),
      model = Some("sonnet"),
    )
    assertEquals(agent.description, "Test agent")
    assertEquals(agent.tools, Some(List("Bash", "Read")))
  }

  test("PermissionUpdate creation") {
    val update = PermissionUpdate(
      updateType = PermissionUpdateType.AddRules,
      rules = Some(List(PermissionRuleValue("Bash", Some("allow all")))),
      behavior = Some(PermissionBehavior.Allow),
      destination = Some(PermissionUpdateDestination.Session),
    )
    assertEquals(update.updateType, PermissionUpdateType.AddRules)
    assert(update.rules.isDefined)
  }

  test("HookMatcher creation") {
    val matcher = HookMatcher(
      matcher = Some("Bash|Write"),
      hooks = Nil,
      timeout = Some(30.0),
    )
    assertEquals(matcher.matcher, Some("Bash|Write"))
    assertEquals(matcher.timeout, Some(30.0))
  }
