package io.github.kapunga.claude.sdk.examples

import cats.effect.{IO, IOApp}

import io.github.kapunga.claude.sdk.types.*
import io.github.kapunga.claude.sdk.query as claudeQuery

object QuickStart extends IOApp.Simple:

  def run: IO[Unit] =
    claudeQuery(prompt = "What is 2+2? Reply with just the number.")
      .evalMap {
        case msg: AssistantMessage =>
          IO.println(s"Assistant: ${msg.content.collect { case TextBlock(t) => t }.mkString}")
        case msg: ResultMessage =>
          IO.println(s"Done! Cost: ${msg.totalCostUsd.getOrElse(0.0)} USD, Turns: ${msg.numTurns}")
        case msg =>
          IO.println(s"[${msg.getClass.getSimpleName}]")
      }
      .compile
      .drain
