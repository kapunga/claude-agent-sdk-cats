package io.github.kapunga.claude.sdk.internal

import cats.effect.IO

import io.circe.JsonObject

import io.github.kapunga.claude.sdk.codec.MessageCodecs.given
import io.github.kapunga.claude.sdk.errors.MessageParseError
import io.github.kapunga.claude.sdk.types.Message

/** Parse raw JSON objects from the CLI into typed Message objects. */
object MessageParser:

  /**
   * Parse a JSON object into a Message.
   *
   * Returns None for unrecognized message types (forward-compatible behavior).
   * Raises MessageParseError for malformed messages.
   */
  def parse(data: JsonObject): IO[Option[Message]] =
    io.circe.Json.fromJsonObject(data).as[Message] match
      case Right(message) => IO.pure(Some(message))
      case Left(failure) =>
        // Check if it's an unknown type (forward-compatible: skip)
        data("type") match
          case Some(typeJson) =>
            typeJson.asString match
              case Some(t) if isKnownType(t) =>
                IO.raiseError(
                  new MessageParseError(
                    s"Failed to parse $t message: ${failure.message}",
                    Some(data),
                  )
                )
              case _ =>
                // Unknown type - skip silently for forward compatibility
                IO.pure(None)
          case None =>
            IO.raiseError(new MessageParseError("Message missing 'type' field", Some(data)))

  private def isKnownType(t: String): Boolean =
    t == "user" || t == "assistant" || t == "system" || t == "result" || t == "stream_event"
