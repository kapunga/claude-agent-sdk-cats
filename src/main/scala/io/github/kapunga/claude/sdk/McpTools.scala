package io.github.kapunga.claude.sdk

import cats.effect.IO

import io.circe.syntax.*
import io.circe.{Decoder, JsonObject}

import sttp.apispec.{Schema => ASchema}
import sttp.apispec.circe.*
import sttp.tapir.{Schema => TSchema}
import sttp.tapir.docs.apispec.schema.TapirSchemaToJsonSchema

import io.github.kapunga.claude.sdk.types.{McpToolHandler, SdkMcpTool}

/** Builder for creating SDK MCP tools. */
object McpTools:

  /** Create an SdkMcpTool with an explicit JSON schema and raw handler. */
  def tool(
    name: String,
    description: String,
    inputSchema: JsonObject,
    handler: McpToolHandler,
  ): SdkMcpTool =
    SdkMcpTool(name, description, inputSchema, handler)

  /** Create an SdkMcpTool with auto-derived JSON Schema and typed input decoding.
   *
   * Requires `TSchema[A]` (from `import sttp.tapir.generic.auto.*` or
   * `TSchema.derived`) and `Decoder[A]` (from circe derivation).
   */
  def tool[A: TSchema: Decoder](
    name: String,
    description: String,
    handler: A => IO[JsonObject],
  ): SdkMcpTool =
    val inputSchema = schemaFor[A]
    val rawHandler: McpToolHandler = { args =>
      args.toJson.as[A] match
        case Right(decoded) => handler(decoded)
        case Left(err) =>
          IO.raiseError(new IllegalArgumentException(s"Failed to decode tool args: ${err.message}"))
    }
    SdkMcpTool(name, description, inputSchema, rawHandler)

  private def schemaFor[A: TSchema]: JsonObject =
    val apiSchema: ASchema = TapirSchemaToJsonSchema(
      summon[TSchema[A]],
      markOptionsAsNullable = true,
    )
    // TapirSchemaToJsonSchema adds $schema and title metadata that MCP doesn't need.
    // Strip them for a clean tool input schema.
    apiSchema.asJson.deepDropNullValues.asObject
      .map(_.remove("$schema").remove("title"))
      .getOrElse(JsonObject.empty)
