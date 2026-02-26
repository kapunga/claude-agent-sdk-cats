package io.github.kapunga.claude.sdk

import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.codec.WireEnum
import io.github.kapunga.claude.sdk.types.{McpToolHandler, SdkMcpTool}

/** JSON Schema types for MCP tool parameters. */
enum JsonSchemaType(val wireValue: String) extends WireEnum:
  case StringType  extends JsonSchemaType("string")
  case IntType     extends JsonSchemaType("integer")
  case NumberType  extends JsonSchemaType("number")
  case BooleanType extends JsonSchemaType("boolean")

object JsonSchemaType extends WireEnum.Companion[JsonSchemaType](JsonSchemaType.values)

/** Builder for creating SDK MCP tools. */
object McpTools:

  /** Create an SdkMcpTool from a handler function and schema.
    *
    * @param name Unique identifier for the tool
    * @param description Human-readable description of what the tool does
    * @param inputSchema JSON Schema describing the tool's input parameters
    * @param handler The function that executes the tool
    */
  def tool(
      name: String,
      description: String,
      inputSchema: JsonObject,
      handler: McpToolHandler,
  ): SdkMcpTool =
    SdkMcpTool(name, description, inputSchema, handler)

  /** Create a simple JSON Schema for a tool with typed parameters. */
  def simpleSchema(params: (String, JsonSchemaType)*): JsonObject =
    val properties = JsonObject.fromIterable(
      params.map { case (name, schemaType) =>
        name -> Json.obj("type" -> schemaType.wireValue.asJson)
      }
    )
    JsonObject(
      "type"       -> "object".asJson,
      "properties" -> Json.fromJsonObject(properties),
      "required"   -> Json.arr(params.map(_._1.asJson)*),
    )
