package io.github.kapunga.claude.sdk

import cats.effect.IO
import fs2.Stream
import io.circe.JsonObject
import io.github.kapunga.claude.sdk.internal.InternalClient
import io.github.kapunga.claude.sdk.transport.Transport
import io.github.kapunga.claude.sdk.types.*

/** Query Claude Code for one-shot interactions.
  *
  * Returns a stream of Messages. Ideal for simple, stateless queries.
  *
  * @param prompt The prompt to send to Claude
  * @param options Optional configuration
  * @param transport Optional custom transport implementation
  */
def query(
    prompt: String,
    options: ClaudeAgentOptions = ClaudeAgentOptions(),
    transport: Option[Transport] = None,
): Stream[IO, Message] =
  InternalClient.processQuery(Left(prompt), options, transport)

/** Query Claude Code with a streaming input.
  *
  * Returns a stream of Messages. Each input message should be a JSON object
  * conforming to the Claude Code stream-json protocol.
  *
  * @param input Stream of JSON message objects to send
  * @param options Optional configuration
  * @param transport Optional custom transport implementation
  */
def queryStreaming(
    input: Stream[IO, JsonObject],
    options: ClaudeAgentOptions = ClaudeAgentOptions(),
    transport: Option[Transport] = None,
): Stream[IO, Message] =
  InternalClient.processQuery(Right(input), options, transport)
