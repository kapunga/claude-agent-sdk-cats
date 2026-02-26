package io.github.kapunga.claude.sdk.types

import cats.effect.IO

import io.circe.JsonObject

import io.github.kapunga.claude.sdk.codec.WireEnum

/** Permission modes controlling how tools are authorized. */
enum PermissionMode(val wireValue: String) extends WireEnum:
  case Default extends PermissionMode("default")
  case AcceptEdits extends PermissionMode("acceptEdits")
  case Plan extends PermissionMode("plan")
  case BypassPermissions extends PermissionMode("bypassPermissions")

object PermissionMode extends WireEnum.Companion[PermissionMode](PermissionMode.values)

/** Behaviors for permission rules. */
enum PermissionBehavior(val wireValue: String) extends WireEnum:
  case Allow extends PermissionBehavior("allow")
  case Deny extends PermissionBehavior("deny")
  case Ask extends PermissionBehavior("ask")

object PermissionBehavior extends WireEnum.Companion[PermissionBehavior](PermissionBehavior.values)

/** Destination for permission updates. */
enum PermissionUpdateDestination(val wireValue: String) extends WireEnum:
  case UserSettings extends PermissionUpdateDestination("userSettings")
  case ProjectSettings extends PermissionUpdateDestination("projectSettings")
  case LocalSettings extends PermissionUpdateDestination("localSettings")
  case Session extends PermissionUpdateDestination("session")

object PermissionUpdateDestination
    extends WireEnum.Companion[PermissionUpdateDestination](PermissionUpdateDestination.values)

/** A single permission rule value. */
final case class PermissionRuleValue(
  toolName: String,
  ruleContent: Option[String] = None,
)

/** Types of permission updates. */
enum PermissionUpdateType(val wireValue: String) extends WireEnum:
  case AddRules extends PermissionUpdateType("addRules")
  case ReplaceRules extends PermissionUpdateType("replaceRules")
  case RemoveRules extends PermissionUpdateType("removeRules")
  case SetMode extends PermissionUpdateType("setMode")
  case AddDirectories extends PermissionUpdateType("addDirectories")
  case RemoveDirectories extends PermissionUpdateType("removeDirectories")

object PermissionUpdateType
    extends WireEnum.Companion[PermissionUpdateType](PermissionUpdateType.values)

/** Permission update configuration. */
final case class PermissionUpdate(
  updateType: PermissionUpdateType,
  rules: Option[List[PermissionRuleValue]] = None,
  behavior: Option[PermissionBehavior] = None,
  mode: Option[PermissionMode] = None,
  directories: Option[List[String]] = None,
  destination: Option[PermissionUpdateDestination] = None,
)

/** Context information for tool permission callbacks. */
final case class ToolPermissionContext(
  suggestions: List[PermissionUpdate] = Nil
)

/** Permission result - allow with optional modifications. */
final case class PermissionResultAllow(
  updatedInput: Option[JsonObject] = None,
  updatedPermissions: Option[List[PermissionUpdate]] = None,
)

/** Permission result - deny with reason. */
final case class PermissionResultDeny(
  message: String = "",
  interrupt: Boolean = false,
)

/** Result of a permission check. */
enum PermissionResult:
  case Allow(result: PermissionResultAllow)
  case Deny(result: PermissionResultDeny)

/** Callback type for tool permission checks. */
type CanUseTool = (String, JsonObject, ToolPermissionContext) => IO[PermissionResult]
