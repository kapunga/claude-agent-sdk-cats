package io.github.kapunga.claude.sdk.types

import cats.effect.IO
import io.circe.JsonObject

/** MCP server configuration variants. */
sealed trait McpServerConfig

/** MCP stdio server configuration. */
final case class McpStdioServerConfig(
    command: String,
    args: Option[List[String]] = None,
    env: Option[Map[String, String]] = None,
) extends McpServerConfig

/** MCP SSE server configuration. */
final case class McpSSEServerConfig(
    url: String,
    headers: Option[Map[String, String]] = None,
) extends McpServerConfig

/** MCP HTTP server configuration. */
final case class McpHttpServerConfig(
    url: String,
    headers: Option[Map[String, String]] = None,
) extends McpServerConfig

/** SDK plugin configuration. */
final case class SdkPluginConfig(
    path: String,
)

/** MCP tool handler callback type. */
type McpToolHandler = JsonObject => IO[JsonObject]

/** Definition for an SDK MCP tool. */
final case class SdkMcpTool(
    name: String,
    description: String,
    inputSchema: JsonObject,
    handler: McpToolHandler,
)
