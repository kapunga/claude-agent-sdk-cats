package io.github.kapunga.claude.sdk.transport

import cats.effect.IO
import fs2.Stream
import io.circe.JsonObject

/** Abstract transport for Claude communication.
  *
  * WARNING: This internal API is exposed for custom transport implementations
  * (e.g., remote Claude Code connections). The interface may change in future releases.
  *
  * This is a low-level transport interface that handles raw I/O with the Claude
  * process or service. The Query class builds on top of this to implement the
  * control protocol and message routing.
  */
trait Transport:
  /** Write raw data to the transport.
    * @param data Raw string data to write (typically JSON + newline)
    */
  def write(data: String): IO[Unit]

  /** Read and parse messages from the transport as a stream. */
  def readMessages: Stream[IO, JsonObject]

  /** End the input stream (close stdin for process transports). */
  def endInput: IO[Unit]

  /** Check if transport is ready for communication. */
  def isReady: IO[Boolean]
