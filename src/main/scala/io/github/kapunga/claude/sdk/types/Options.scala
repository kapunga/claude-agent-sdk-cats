package io.github.kapunga.claude.sdk.types

import io.circe.JsonObject

/** SDK Beta features. */
enum SdkBeta(val value: String):
  case Context1m extends SdkBeta("context-1m-2025-08-07")

/** Setting source types. */
enum SettingSource:
  case User, Project, Local

/** System prompt preset configuration. */
final case class SystemPromptPreset(
    preset: String, // "claude_code"
    append: Option[String] = None,
)

/** System prompt - either a plain string or a preset. */
enum SystemPrompt:
  case Text(value: String)
  case Preset(preset: SystemPromptPreset)

/** Tools preset configuration. */
final case class ToolsPreset(
    preset: String, // "claude_code"
)

/** Tools configuration - either a list of tool names or a preset. */
enum ToolsConfig:
  case ToolList(tools: List[String])
  case Preset(preset: ToolsPreset)

/** Agent definition configuration. */
final case class AgentDefinition(
    description: String,
    prompt: String,
    tools: Option[List[String]] = None,
    model: Option[String] = None,
)

/** Network configuration for sandbox. */
final case class SandboxNetworkConfig(
    allowUnixSockets: Option[List[String]] = None,
    allowAllUnixSockets: Option[Boolean] = None,
    allowLocalBinding: Option[Boolean] = None,
    httpProxyPort: Option[Int] = None,
    socksProxyPort: Option[Int] = None,
)

/** Violations to ignore in sandbox. */
final case class SandboxIgnoreViolations(
    file: Option[List[String]] = None,
    network: Option[List[String]] = None,
)

/** Sandbox settings configuration. */
final case class SandboxSettings(
    enabled: Option[Boolean] = None,
    autoAllowBashIfSandboxed: Option[Boolean] = None,
    excludedCommands: Option[List[String]] = None,
    allowUnsandboxedCommands: Option[Boolean] = None,
    network: Option[SandboxNetworkConfig] = None,
    ignoreViolations: Option[SandboxIgnoreViolations] = None,
    enableWeakerNestedSandbox: Option[Boolean] = None,
)

/** Thinking configuration variants. */
enum ThinkingConfig:
  case Adaptive
  case Enabled(budgetTokens: Int)
  case Disabled

/** Effort level for thinking depth. */
enum Effort:
  case Low, Medium, High, Max

/** MCP servers configuration - either a map or a path/JSON string. */
enum McpServersConfig:
  case ServerMap(servers: Map[String, McpServerConfig])
  case PathOrJson(value: String)

/** Output format for structured outputs. */
final case class OutputFormat(
    formatType: String, // "json_schema"
    schema: Option[JsonObject] = None,
)

/** Query options for Claude SDK. */
final case class ClaudeAgentOptions(
    tools: Option[ToolsConfig] = None,
    allowedTools: List[String] = Nil,
    systemPrompt: Option[SystemPrompt] = None,
    mcpServers: McpServersConfig = McpServersConfig.ServerMap(Map.empty),
    permissionMode: Option[PermissionMode] = None,
    continueConversation: Boolean = false,
    resume: Option[String] = None,
    maxTurns: Option[Int] = None,
    maxBudgetUsd: Option[Double] = None,
    disallowedTools: List[String] = Nil,
    model: Option[String] = None,
    fallbackModel: Option[String] = None,
    betas: List[SdkBeta] = Nil,
    permissionPromptToolName: Option[String] = None,
    cwd: Option[String] = None,
    cliPath: Option[String] = None,
    settings: Option[String] = None,
    addDirs: List[String] = Nil,
    env: Map[String, String] = Map.empty,
    extraArgs: Map[String, Option[String]] = Map.empty,
    maxBufferSize: Option[Int] = None,
    stderrCallback: Option[String => Unit] = None,
    canUseTool: Option[CanUseTool] = None,
    hooks: Option[Map[HookEvent, List[HookMatcher]]] = None,
    user: Option[String] = None,
    includePartialMessages: Boolean = false,
    forkSession: Boolean = false,
    agents: Option[Map[String, AgentDefinition]] = None,
    settingSources: Option[List[SettingSource]] = None,
    sandbox: Option[SandboxSettings] = None,
    plugins: List[SdkPluginConfig] = Nil,
    maxThinkingTokens: Option[Int] = None,
    thinking: Option[ThinkingConfig] = None,
    effort: Option[Effort] = None,
    outputFormat: Option[OutputFormat] = None,
    enableFileCheckpointing: Boolean = false,
)
