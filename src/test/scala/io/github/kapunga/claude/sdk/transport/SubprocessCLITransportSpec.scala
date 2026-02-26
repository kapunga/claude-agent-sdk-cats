package io.github.kapunga.claude.sdk.transport

import munit.CatsEffectSuite

import io.github.kapunga.claude.sdk.types.*

class SubprocessCLITransportSpec extends CatsEffectSuite:

  test("buildCommand produces correct base command") {
    val options = ClaudeAgentOptions()
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--output-format"))
    assert(cmd.contains("stream-json"))
    assert(cmd.contains("--verbose"))
    assert(cmd.contains("--input-format"))
    assertEquals(cmd.head, "/usr/bin/claude")
  }

  test("buildCommand includes system prompt") {
    val options = ClaudeAgentOptions(
      systemPrompt = Some(SystemPrompt.Text("You are helpful"))
    )
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--system-prompt"))
    assert(cmd.contains("You are helpful"))
  }

  test("buildCommand includes empty system prompt when None") {
    val options = ClaudeAgentOptions(systemPrompt = None)
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    val idx = cmd.indexOf("--system-prompt")
    assert(idx >= 0)
    assertEquals(cmd(idx + 1), "")
  }

  test("buildCommand includes tools") {
    val options = ClaudeAgentOptions(
      tools = Some(ToolsConfig.ToolList(List("Bash", "Read")))
    )
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--tools"))
    assert(cmd.contains("Bash,Read"))
  }

  test("buildCommand includes model") {
    val options = ClaudeAgentOptions(model = Some("claude-opus-4-1-20250805"))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--model"))
    assert(cmd.contains("claude-opus-4-1-20250805"))
  }

  test("buildCommand includes permission mode") {
    val options = ClaudeAgentOptions(permissionMode = Some(PermissionMode.BypassPermissions))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--permission-mode"))
    assert(cmd.contains("bypassPermissions"))
  }

  test("buildCommand includes max turns") {
    val options = ClaudeAgentOptions(maxTurns = Some(5))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--max-turns"))
    assert(cmd.contains("5"))
  }

  test("buildCommand includes extra args") {
    val options = ClaudeAgentOptions(
      extraArgs = Map("debug-to-stderr" -> None, "custom-flag" -> Some("value"))
    )
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--debug-to-stderr"))
    assert(cmd.contains("--custom-flag"))
    assert(cmd.contains("value"))
  }

  test("buildCommand includes thinking config - adaptive") {
    val options = ClaudeAgentOptions(thinking = Some(ThinkingConfig.Adaptive))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--max-thinking-tokens"))
    assert(cmd.contains("32000"))
  }

  test("buildCommand includes thinking config - enabled") {
    val options = ClaudeAgentOptions(thinking = Some(ThinkingConfig.Enabled(16000)))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--max-thinking-tokens"))
    assert(cmd.contains("16000"))
  }

  test("buildCommand includes thinking config - disabled") {
    val options = ClaudeAgentOptions(thinking = Some(ThinkingConfig.Disabled))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--max-thinking-tokens"))
    assert(cmd.contains("0"))
  }

  test("buildCommand includes effort") {
    val options = ClaudeAgentOptions(effort = Some(Effort.High))
    val cmd = SubprocessCLITransport.buildCommand("/usr/bin/claude", options)
    assert(cmd.contains("--effort"))
    assert(cmd.contains("high"))
  }

  test("jsonBufferPipe parses complete JSON lines") {
    import cats.effect.IO
    val input = fs2.Stream.emits(
      List(
        """{"type": "assistant", "message": {"content": [], "model": "test"}}"""
      )
    )
    val result = input
      .through(SubprocessCLITransport.jsonBufferPipe(1024 * 1024))
      .compile
      .toList
      .unsafeRunSync()
    assertEquals(result.length, 1)
    assertEquals(result.head("type").flatMap(_.asString), Some("assistant"))
  }

  test("jsonBufferPipe buffers partial JSON") {
    import cats.effect.IO
    val input = fs2.Stream.emits(
      List(
        """{"type": "ass""",
        """istant", "data": {}}""",
      )
    )
    val result = input
      .through(SubprocessCLITransport.jsonBufferPipe(1024 * 1024))
      .compile
      .toList
      .unsafeRunSync()
    assertEquals(result.length, 1)
    assertEquals(result.head("type").flatMap(_.asString), Some("assistant"))
  }

  test("jsonBufferPipe skips empty lines") {
    import cats.effect.IO
    val input = fs2.Stream.emits(List("", "  ", """{"ok": true}""", ""))
    val result = input
      .through(SubprocessCLITransport.jsonBufferPipe(1024 * 1024))
      .compile
      .toList
      .unsafeRunSync()
    assertEquals(result.length, 1)
  }
