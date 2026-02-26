package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*

import io.github.kapunga.claude.sdk.codec.DerivedCodec.encoderDropNulls
import io.github.kapunga.claude.sdk.types.*

/**
 * Codecs for ClaudeAgentOptions - primarily used for command building.
 * These encoders produce JSON where needed (e.g. sandbox settings, MCP config).
 */
object OptionsCodecs:

  // SettingSource, SdkBeta, Effort codecs are provided by their WireEnum companions — import via types.*

  given Encoder[SandboxNetworkConfig] = encoderDropNulls
  given Encoder[SandboxIgnoreViolations] = encoderDropNulls
  given Encoder[SandboxSettings] = encoderDropNulls
  given Encoder[AgentDefinition] = encoderDropNulls

  given Encoder[ThinkingConfig] = Encoder.instance {
    case ThinkingConfig.Adaptive => Json.obj("type" -> "adaptive".asJson)
    case ThinkingConfig.Enabled(b) =>
      Json.obj("type" -> "enabled".asJson, "budget_tokens" -> b.asJson)
    case ThinkingConfig.Disabled => Json.obj("type" -> "disabled".asJson)
  }
