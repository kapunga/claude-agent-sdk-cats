package io.github.kapunga.claude.sdk.internal

import cats.effect.{IO, Ref}
import cats.syntax.all.*

import fs2.Stream

import io.circe.syntax.*
import io.circe.{Json, JsonObject}

import io.github.kapunga.claude.sdk.transport.{SubprocessCLITransport, Transport}
import io.github.kapunga.claude.sdk.types.*

/** Shared logic for processing queries through transport + query + message parsing. */
object InternalClient:

  /** Build agents JSON from agent definitions. */
  private[sdk] def buildAgentsJson(
    agents: Map[String, AgentDefinition]
  ): Map[String, JsonObject] =
    agents.map { case (name, defn) =>
      name -> Json
        .obj(
          "description" -> defn.description.asJson,
          "prompt" -> defn.prompt.asJson,
          "tools" -> defn.tools.asJson,
          "model" -> defn.model.asJson,
        )
        .dropNullValues
        .asObject
        .getOrElse(JsonObject.empty)
    }

  /** Validate and configure options before processing a query. */
  private def validateOptions(
    prompt: Either[String, Stream[IO, JsonObject]],
    options: ClaudeAgentOptions,
  ): IO[ClaudeAgentOptions] =
    options.canUseTool match
      case Some(_) if prompt.isLeft =>
        IO.raiseError(
          new IllegalArgumentException(
            "can_use_tool callback requires streaming mode. " +
              "Please provide prompt as a Stream instead of a string."
          )
        )
      case Some(_) if options.permissionPromptToolName.isDefined =>
        IO.raiseError(
          new IllegalArgumentException(
            "can_use_tool callback cannot be used with permissionPromptToolName. " +
              "Please use one or the other."
          )
        )
      case Some(_) =>
        IO.pure(options.copy(permissionPromptToolName = Some("stdio")))
      case None =>
        IO.pure(options)

  /**
   * Process a query, yielding parsed Messages.
   *
   * This creates the transport, initializes the control protocol,
   * sends the prompt, and streams back parsed messages.
   */
  def processQuery(
    prompt: Either[String, Stream[IO, JsonObject]],
    options: ClaudeAgentOptions,
    customTransport: Option[Transport] = None,
  ): Stream[IO, Message] =
    Stream.eval(validateOptions(prompt, options)).flatMap { configuredOptions =>
      val transportStream = customTransport match
        case Some(t) => Stream.emit(t)
        case None => Stream.resource(SubprocessCLITransport.resource(configuredOptions))

      transportStream.flatMap { transport =>
        Stream
          .eval(
            for
              hookCallbacksRef <- Ref.of[IO, Map[String, HookCallback]](Map.empty)
              internalHooks <- configuredOptions.hooks
                .traverse(h => Query.convertHooks(h, hookCallbacksRef))
              agentsJson = configuredOptions.agents.map(buildAgentsJson)
              queryConfig = QueryConfig(
                canUseTool = configuredOptions.canUseTool,
                hooks = internalHooks,
                agents = agentsJson,
              )
              query <- Query.make(transport, queryConfig)
            yield (transport, query, hookCallbacksRef)
          )
          .flatMap { case (transport, query, _) =>
            // Read messages from transport, route through query
            val messageProcessing = transport.readMessages.through(query.processMessages)

            // Initialize and send prompt
            val init = Stream.exec(query.initialize.void)

            val sendPrompt = prompt match
              case Left(text) =>
                val userMessage = JsonObject(
                  "type" -> "user".asJson,
                  "session_id" -> "".asJson,
                  "message" -> Json.obj("role" -> "user".asJson, "content" -> text.asJson),
                  "parent_tool_use_id" -> Json.Null,
                )
                Stream.exec(
                  transport
                    .write(Json.fromJsonObject(userMessage).noSpaces + "\n") >> transport.endInput
                )
              case Right(inputStream) =>
                Stream.exec(query.streamInput(inputStream))

            // Merge: process messages concurrently while sending prompt
            val pipeline =
              init ++ query.receiveMessages.concurrently(messageProcessing.merge(sendPrompt))

            // Parse each JSON object into a Message
            pipeline.evalMap(MessageParser.parse).unNone
          }
      }
    }
