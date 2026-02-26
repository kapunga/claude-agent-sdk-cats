package io.github.kapunga.claude.sdk

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Supervisor
import cats.syntax.all.*
import fs2.Stream
import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.internal.{Query, QueryConfig}
import io.github.kapunga.claude.sdk.internal.MessageParser
import io.github.kapunga.claude.sdk.transport.{SubprocessCLITransport, Transport}
import io.github.kapunga.claude.sdk.types.*

/** Client for bidirectional, interactive conversations with Claude Code.
  *
  * This client provides full control over the conversation flow with support
  * for streaming, interrupts, and dynamic message sending.
  *
  * Use `ClaudeSDKClient.resource` to create an instance with proper lifecycle management.
  */
trait ClaudeSDKClient:
  /** Send a query (string prompt or message stream). */
  def query(prompt: String, sessionId: String = "default"): IO[Unit]

  /** Send a stream of messages. */
  def queryStream(messages: Stream[IO, JsonObject], sessionId: String = "default"): IO[Unit]

  /** Receive all messages from Claude. */
  def receiveMessages: Stream[IO, Message]

  /** Receive messages until and including the next ResultMessage. */
  def receiveResponse: Stream[IO, Message]

  /** Send interrupt signal. */
  def interrupt: IO[Unit]

  /** Change permission mode during conversation. */
  def setPermissionMode(mode: PermissionMode): IO[Unit]

  /** Change the AI model during conversation. */
  def setModel(model: Option[String] = None): IO[Unit]

  /** Rewind tracked files to a specific user message. */
  def rewindFiles(userMessageId: String): IO[Unit]

  /** Get MCP server connection status. */
  def getMcpStatus: IO[JsonObject]

  /** Get server initialization info. */
  def getServerInfo: IO[Option[JsonObject]]

object ClaudeSDKClient:

  /** Create a ClaudeSDKClient as a Resource with proper lifecycle management.
    *
    * The client connects on acquisition and disconnects on release.
    */
  def resource(
      options: ClaudeAgentOptions = ClaudeAgentOptions(),
      customTransport: Option[Transport] = None,
  ): Resource[IO, ClaudeSDKClient] =
    for
      configuredOptions <- Resource.eval(IO {
        options.canUseTool match
          case Some(_) if options.permissionPromptToolName.isDefined =>
            throw new IllegalArgumentException(
              "can_use_tool callback cannot be used with permissionPromptToolName."
            )
          case Some(_) =>
            options.copy(permissionPromptToolName = Some("stdio"))
          case None =>
            options
      })
      transport <- customTransport match
        case Some(t) => Resource.pure[IO, Transport](t)
        case None    => SubprocessCLITransport.resource(configuredOptions)
      hookCallbacksRef <- Resource.eval(Ref.of[IO, Map[String, HookCallback]](Map.empty))
      internalHooks <- Resource.eval(
        configuredOptions.hooks.traverse(h =>
          Query.convertHooks(h, hookCallbacksRef)
        )
      )
      agentsJson = configuredOptions.agents.map { agents =>
        agents.map { case (name, defn) =>
          val fields = List.newBuilder[(String, Json)]
          fields += ("description" -> defn.description.asJson)
          fields += ("prompt" -> defn.prompt.asJson)
          defn.tools.foreach(t => fields += ("tools" -> t.asJson))
          defn.model.foreach(m => fields += ("model" -> m.asJson))
          name -> JsonObject.fromIterable(fields.result())
        }
      }
      queryConfig = QueryConfig(
        canUseTool = configuredOptions.canUseTool,
        hooks = internalHooks,
        agents = agentsJson,
      )
      queryInst <- Query.resource(transport, queryConfig)
      // Start processing messages from transport in background via Supervisor
      sup <- Supervisor[IO](await = false)
      _ <- Resource.eval(
        sup.supervise(
          transport.readMessages.through(queryInst.processMessages).compile.drain
        ).void
      )
      // Initialize the control protocol
      _ <- Resource.eval(queryInst.initialize)
    yield new ClaudeSDKClientImpl(transport, queryInst)

  private class ClaudeSDKClientImpl(
      transport: Transport,
      queryInst: Query,
  ) extends ClaudeSDKClient:

    def query(prompt: String, sessionId: String): IO[Unit] =
      val message = JsonObject(
        "type"               -> "user".asJson,
        "message"            -> Json.obj("role" -> "user".asJson, "content" -> prompt.asJson),
        "parent_tool_use_id" -> Json.Null,
        "session_id"         -> sessionId.asJson,
      )
      transport.write(Json.fromJsonObject(message).noSpaces + "\n")

    def queryStream(messages: Stream[IO, JsonObject], sessionId: String): IO[Unit] =
      messages.evalMap { msg =>
        val withSession = if msg.contains("session_id") then msg
          else msg.add("session_id", sessionId.asJson)
        transport.write(Json.fromJsonObject(withSession).noSpaces + "\n")
      }.compile.drain

    def receiveMessages: Stream[IO, Message] =
      queryInst.receiveMessages.evalMap(MessageParser.parse).unNone

    def receiveResponse: Stream[IO, Message] =
      receiveMessages.takeThrough {
        case _: ResultMessage => true
        case _                => false
      }

    def interrupt: IO[Unit] = queryInst.interrupt
    def setPermissionMode(mode: PermissionMode): IO[Unit] = queryInst.setPermissionMode(mode)
    def setModel(model: Option[String]): IO[Unit] = queryInst.setModel(model)
    def rewindFiles(userMessageId: String): IO[Unit] = queryInst.rewindFiles(userMessageId)
    def getMcpStatus: IO[JsonObject] = queryInst.getMcpStatus
    def getServerInfo: IO[Option[JsonObject]] = queryInst.initializationResult
