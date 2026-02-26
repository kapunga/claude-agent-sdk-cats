package io.github.kapunga.claude.sdk

import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.types.{McpToolHandler, SdkMcpTool}

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

  /** Create a simple JSON Schema for a tool with named string parameters. */
  def simpleSchema(params: (String, String)*): JsonObject =
    val properties = JsonObject.fromIterable(
      params.map { case (name, typeName) =>
        val jsonType = typeName match
          case "string"  => "string"
          case "int"     => "integer"
          case "integer" => "integer"
          case "number"  => "number"
          case "float"   => "number"
          case "boolean" => "boolean"
          case _         => "string"
        name -> Json.obj("type" -> jsonType.asJson)
      }
    )
    JsonObject(
      "type"       -> "object".asJson,
      "properties" -> Json.fromJsonObject(properties),
      "required"   -> Json.arr(params.map(_._1.asJson)*),
    )
