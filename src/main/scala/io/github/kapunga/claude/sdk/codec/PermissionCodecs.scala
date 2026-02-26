package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.types.*

object PermissionCodecs:

  // PermissionMode, PermissionBehavior, PermissionUpdateDestination, PermissionUpdateType
  // codecs are provided by their WireEnum companions — import via types.*

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
