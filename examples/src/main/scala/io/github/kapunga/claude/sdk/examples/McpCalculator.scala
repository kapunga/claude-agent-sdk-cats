package io.github.kapunga.claude.sdk.examples

import cats.effect.{IO, IOApp}

import io.circe.syntax.*
import io.circe.{Decoder, Json, JsonObject}

import sttp.tapir.generic.auto.*
import sttp.tapir.Schema as TSchema

import io.github.kapunga.claude.sdk.McpTools

final case class BinaryOpInput(a: Double, b: Double) derives Decoder

object McpCalculator extends IOApp.Simple:

  // TSchema[BinaryOpInput] is derived automatically via sttp.tapir.generic.auto.*

  def run: IO[Unit] =
    val addTool = McpTools.tool[BinaryOpInput](
      name = "add",
      description = "Add two numbers",
      handler = { input =>
        val result = input.a + input.b
        IO.pure(
          JsonObject(
            "content" -> Json.arr(
              Json.obj("type" -> "text".asJson, "text" -> s"Result: $result".asJson)
            )
          )
        )
      },
    )

    val multiplyTool = McpTools.tool[BinaryOpInput](
      name = "multiply",
      description = "Multiply two numbers",
      handler = { input =>
        val result = input.a * input.b
        IO.pure(
          JsonObject(
            "content" -> Json.arr(
              Json.obj("type" -> "text".asJson, "text" -> s"Result: $result".asJson)
            )
          )
        )
      },
    )

    IO.println("MCP Calculator tools defined: add, multiply") >>
      IO.println("Note: In-process MCP server support requires MCP library integration.") >>
      IO.println(s"Add tool: ${addTool.name} - ${addTool.description}") >>
      IO.println(s"Multiply tool: ${multiplyTool.name} - ${multiplyTool.description}")
