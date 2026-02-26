package io.github.kapunga.claude.sdk.examples

import cats.effect.{IO, IOApp}
import io.github.kapunga.claude.sdk.ClaudeSDKClient
import io.github.kapunga.claude.sdk.types.*

object StreamingMode extends IOApp.Simple:

  def run: IO[Unit] =
    val options = ClaudeAgentOptions(
      permissionMode = Some(PermissionMode.Default),
      systemPrompt = Some(SystemPrompt.Text("You are a helpful assistant. Be concise.")),
      maxTurns = Some(3),
    )

    ClaudeSDKClient.resource(options).use { client =>
      for
        // Send first query
        _ <- client.query("What is the capital of France?")
        // Receive response
        _ <- client.receiveResponse.evalMap {
          case msg: AssistantMessage =>
            IO.println(s"Claude: ${msg.content.collect { case TextBlock(t) => t }.mkString}")
          case msg: ResultMessage =>
            IO.println(s"[Turn complete - cost: ${msg.totalCostUsd.getOrElse(0.0)} USD]")
          case _ => IO.unit
        }.compile.drain

        // Send follow-up
        _ <- client.query("And what is its population?")
        _ <- client.receiveResponse.evalMap {
          case msg: AssistantMessage =>
            IO.println(s"Claude: ${msg.content.collect { case TextBlock(t) => t }.mkString}")
          case msg: ResultMessage =>
            IO.println(s"[Turn complete - cost: ${msg.totalCostUsd.getOrElse(0.0)} USD]")
          case _ => IO.unit
        }.compile.drain
      yield ()
    }
