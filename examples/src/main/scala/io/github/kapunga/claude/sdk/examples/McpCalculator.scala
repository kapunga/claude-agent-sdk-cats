package io.github.kapunga.claude.sdk.examples

import cats.effect.{IO, IOApp}
import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.McpTools
import io.github.kapunga.claude.sdk.types.*

object McpCalculator extends IOApp.Simple:

  def run: IO[Unit] =
    // Define calculator tools
    val addTool = McpTools.tool(
      name = "add",
      description = "Add two numbers",
      inputSchema = McpTools.simpleSchema("a" -> "number", "b" -> "number"),
      handler = { args =>
        val a = args("a").flatMap(_.asNumber).flatMap(n => Some(n.toDouble)).getOrElse(0.0)
        val b = args("b").flatMap(_.asNumber).flatMap(n => Some(n.toDouble)).getOrElse(0.0)
        val result = a + b
        IO.pure(JsonObject(
          "content" -> Json.arr(Json.obj("type" -> "text".asJson, "text" -> s"Result: $result".asJson)),
        ))
      },
    )

    val multiplyTool = McpTools.tool(
      name = "multiply",
      description = "Multiply two numbers",
      inputSchema = McpTools.simpleSchema("a" -> "number", "b" -> "number"),
      handler = { args =>
        val a = args("a").flatMap(_.asNumber).flatMap(n => Some(n.toDouble)).getOrElse(0.0)
        val b = args("b").flatMap(_.asNumber).flatMap(n => Some(n.toDouble)).getOrElse(0.0)
        val result = a * b
        IO.pure(JsonObject(
          "content" -> Json.arr(Json.obj("type" -> "text".asJson, "text" -> s"Result: $result".asJson)),
        ))
      },
    )

    IO.println("MCP Calculator tools defined: add, multiply") >>
    IO.println("Note: In-process MCP server support requires MCP library integration.") >>
    IO.println(s"Add tool: ${addTool.name} - ${addTool.description}") >>
    IO.println(s"Multiply tool: ${multiplyTool.name} - ${multiplyTool.description}")
