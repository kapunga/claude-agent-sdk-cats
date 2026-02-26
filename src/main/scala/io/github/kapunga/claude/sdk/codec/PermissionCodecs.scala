package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.types.*

object PermissionCodecs:

  given Encoder[PermissionMode] = Encoder[String].contramap {
    case PermissionMode.Default           => "default"
    case PermissionMode.AcceptEdits       => "acceptEdits"
    case PermissionMode.Plan              => "plan"
    case PermissionMode.BypassPermissions => "bypassPermissions"
  }

  given Decoder[PermissionMode] = Decoder[String].emap {
    case "default"           => Right(PermissionMode.Default)
    case "acceptEdits"       => Right(PermissionMode.AcceptEdits)
    case "plan"              => Right(PermissionMode.Plan)
    case "bypassPermissions" => Right(PermissionMode.BypassPermissions)
    case other               => Left(s"Unknown permission mode: $other")
  }

  given Encoder[PermissionBehavior] = Encoder[String].contramap {
    case PermissionBehavior.Allow => "allow"
    case PermissionBehavior.Deny  => "deny"
    case PermissionBehavior.Ask   => "ask"
  }

  given Encoder[PermissionUpdateDestination] = Encoder[String].contramap {
    case PermissionUpdateDestination.UserSettings    => "userSettings"
    case PermissionUpdateDestination.ProjectSettings => "projectSettings"
    case PermissionUpdateDestination.LocalSettings   => "localSettings"
    case PermissionUpdateDestination.Session         => "session"
  }

  given Encoder[PermissionUpdateType] = Encoder[String].contramap {
    case PermissionUpdateType.AddRules          => "addRules"
    case PermissionUpdateType.ReplaceRules      => "replaceRules"
    case PermissionUpdateType.RemoveRules       => "removeRules"
    case PermissionUpdateType.SetMode           => "setMode"
    case PermissionUpdateType.AddDirectories    => "addDirectories"
    case PermissionUpdateType.RemoveDirectories => "removeDirectories"
  }

  given Encoder[PermissionRuleValue] = Encoder.instance { r =>
    Json.obj(
      "toolName"    -> r.toolName.asJson,
      "ruleContent" -> r.ruleContent.asJson,
    )
  }

  given Encoder[PermissionUpdate] = Encoder.instance { p =>
    val fields = List.newBuilder[(String, Json)]
    fields += ("type" -> Encoder[PermissionUpdateType].apply(p.updateType))
    p.destination.foreach(d => fields += ("destination" -> Encoder[PermissionUpdateDestination].apply(d)))

    p.updateType match
      case PermissionUpdateType.AddRules | PermissionUpdateType.ReplaceRules | PermissionUpdateType.RemoveRules =>
        p.rules.foreach(r => fields += ("rules" -> r.asJson))
        p.behavior.foreach(b => fields += ("behavior" -> Encoder[PermissionBehavior].apply(b)))
      case PermissionUpdateType.SetMode =>
        p.mode.foreach(m => fields += ("mode" -> Encoder[PermissionMode].apply(m)))
      case PermissionUpdateType.AddDirectories | PermissionUpdateType.RemoveDirectories =>
        p.directories.foreach(d => fields += ("directories" -> d.asJson))

    Json.fromFields(fields.result())
  }

  given Encoder[PermissionResult] = Encoder.instance {
    case PermissionResult.Allow(r) =>
      val fields = List.newBuilder[(String, Json)]
      fields += ("behavior" -> "allow".asJson)
      r.updatedInput.foreach(ui => fields += ("updatedInput" -> ui.asJson))
      r.updatedPermissions.foreach(up => fields += ("updatedPermissions" -> up.asJson))
      Json.fromFields(fields.result())
    case PermissionResult.Deny(r) =>
      val fields = List.newBuilder[(String, Json)]
      fields += ("behavior" -> "deny".asJson)
      fields += ("message"  -> r.message.asJson)
      if r.interrupt then fields += ("interrupt" -> Json.True)
      Json.fromFields(fields.result())
  }
