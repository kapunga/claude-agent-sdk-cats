package io.github.kapunga.claude.sdk.types

import cats.effect.IO
import io.circe.JsonObject

/** Permission modes controlling how tools are authorized. */
enum PermissionMode:
  case Default, AcceptEdits, Plan, BypassPermissions

/** Behaviors for permission rules. */
enum PermissionBehavior:
  case Allow, Deny, Ask

/** Destination for permission updates. */
enum PermissionUpdateDestination:
  case UserSettings, ProjectSettings, LocalSettings, Session

/** A single permission rule value. */
final case class PermissionRuleValue(
    toolName: String,
    ruleContent: Option[String] = None,
)

/** Types of permission updates. */
enum PermissionUpdateType:
  case AddRules, ReplaceRules, RemoveRules, SetMode, AddDirectories, RemoveDirectories

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
    suggestions: List[PermissionUpdate] = Nil,
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
