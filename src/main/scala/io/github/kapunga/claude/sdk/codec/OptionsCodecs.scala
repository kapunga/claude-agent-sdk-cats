package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.types.*

/** Codecs for ClaudeAgentOptions - primarily used for command building.
  * These encoders produce JSON where needed (e.g. sandbox settings, MCP config).
  */
object OptionsCodecs:

  given Encoder[SandboxNetworkConfig] = Encoder.instance { n =>
    val fields = List.newBuilder[(String, Json)]
    n.allowUnixSockets.foreach(v => fields += ("allowUnixSockets" -> v.asJson))
    n.allowAllUnixSockets.foreach(v => fields += ("allowAllUnixSockets" -> v.asJson))
    n.allowLocalBinding.foreach(v => fields += ("allowLocalBinding" -> v.asJson))
    n.httpProxyPort.foreach(v => fields += ("httpProxyPort" -> v.asJson))
    n.socksProxyPort.foreach(v => fields += ("socksProxyPort" -> v.asJson))
    Json.fromFields(fields.result())
  }

  given Encoder[SandboxIgnoreViolations] = Encoder.instance { i =>
    val fields = List.newBuilder[(String, Json)]
    i.file.foreach(v => fields += ("file" -> v.asJson))
    i.network.foreach(v => fields += ("network" -> v.asJson))
    Json.fromFields(fields.result())
  }

  given Encoder[SandboxSettings] = Encoder.instance { s =>
    val fields = List.newBuilder[(String, Json)]
    s.enabled.foreach(v => fields += ("enabled" -> v.asJson))
    s.autoAllowBashIfSandboxed.foreach(v => fields += ("autoAllowBashIfSandboxed" -> v.asJson))
    s.excludedCommands.foreach(v => fields += ("excludedCommands" -> v.asJson))
    s.allowUnsandboxedCommands.foreach(v => fields += ("allowUnsandboxedCommands" -> v.asJson))
    s.network.foreach(v => fields += ("network" -> v.asJson))
    s.ignoreViolations.foreach(v => fields += ("ignoreViolations" -> v.asJson))
    s.enableWeakerNestedSandbox.foreach(v => fields += ("enableWeakerNestedSandbox" -> v.asJson))
    Json.fromFields(fields.result())
  }

  given Encoder[ThinkingConfig] = Encoder.instance {
    case ThinkingConfig.Adaptive       => Json.obj("type" -> "adaptive".asJson)
    case ThinkingConfig.Enabled(b)     => Json.obj("type" -> "enabled".asJson, "budget_tokens" -> b.asJson)
    case ThinkingConfig.Disabled       => Json.obj("type" -> "disabled".asJson)
  }

  given Encoder[AgentDefinition] = Encoder.instance { a =>
    val fields = List.newBuilder[(String, Json)]
    fields += ("description" -> a.description.asJson)
    fields += ("prompt" -> a.prompt.asJson)
    a.tools.foreach(v => fields += ("tools" -> v.asJson))
    a.model.foreach(v => fields += ("model" -> v.asJson))
    Json.fromFields(fields.result())
  }

  given Encoder[SettingSource] = Encoder[String].contramap {
    case SettingSource.User    => "user"
    case SettingSource.Project => "project"
    case SettingSource.Local   => "local"
  }

  given Encoder[SdkBeta] = Encoder[String].contramap(_.value)

  given Encoder[Effort] = Encoder[String].contramap {
    case Effort.Low    => "low"
    case Effort.Medium => "medium"
    case Effort.High   => "high"
    case Effort.Max    => "max"
  }
