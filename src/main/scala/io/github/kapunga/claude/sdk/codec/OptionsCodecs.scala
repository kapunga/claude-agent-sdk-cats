package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*

import io.github.kapunga.claude.sdk.types.*

/**
 * Codecs for ClaudeAgentOptions - primarily used for command building.
 * These encoders produce JSON where needed (e.g. sandbox settings, MCP config).
 */
object OptionsCodecs:

  // SettingSource, SdkBeta, Effort codecs are provided by their WireEnum companions — import via types.*

  given Encoder[SandboxNetworkConfig] = Encoder.instance { n =>
    Json
      .obj(
        "allowUnixSockets" -> n.allowUnixSockets.asJson,
        "allowAllUnixSockets" -> n.allowAllUnixSockets.asJson,
        "allowLocalBinding" -> n.allowLocalBinding.asJson,
        "httpProxyPort" -> n.httpProxyPort.asJson,
        "socksProxyPort" -> n.socksProxyPort.asJson,
      )
      .dropNullValues
  }

  given Encoder[SandboxIgnoreViolations] = Encoder.instance { i =>
    Json
      .obj(
        "file" -> i.file.asJson,
        "network" -> i.network.asJson,
      )
      .dropNullValues
  }

  given Encoder[SandboxSettings] = Encoder.instance { s =>
    Json
      .obj(
        "enabled" -> s.enabled.asJson,
        "autoAllowBashIfSandboxed" -> s.autoAllowBashIfSandboxed.asJson,
        "excludedCommands" -> s.excludedCommands.asJson,
        "allowUnsandboxedCommands" -> s.allowUnsandboxedCommands.asJson,
        "network" -> s.network.asJson,
        "ignoreViolations" -> s.ignoreViolations.asJson,
        "enableWeakerNestedSandbox" -> s.enableWeakerNestedSandbox.asJson,
      )
      .dropNullValues
  }

  given Encoder[ThinkingConfig] = Encoder.instance {
    case ThinkingConfig.Adaptive => Json.obj("type" -> "adaptive".asJson)
    case ThinkingConfig.Enabled(b) =>
      Json.obj("type" -> "enabled".asJson, "budget_tokens" -> b.asJson)
    case ThinkingConfig.Disabled => Json.obj("type" -> "disabled".asJson)
  }

  given Encoder[AgentDefinition] = Encoder.instance { a =>
    Json
      .obj(
        "description" -> a.description.asJson,
        "prompt" -> a.prompt.asJson,
        "tools" -> a.tools.asJson,
        "model" -> a.model.asJson,
      )
      .dropNullValues
  }
