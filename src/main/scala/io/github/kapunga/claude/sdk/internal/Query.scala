package io.github.kapunga.claude.sdk.internal

import scala.concurrent.duration.*

import cats.effect.std.{Queue, Supervisor}
import cats.effect.{Deferred, IO, Ref, Resource}
import cats.syntax.all.*

import fs2.{Pipe, Stream}

import io.circe.syntax.*
import io.circe.{Json, JsonObject}

import io.github.kapunga.claude.sdk.codec.ControlCodecs
import io.github.kapunga.claude.sdk.codec.ControlCodecs.given
import io.github.kapunga.claude.sdk.codec.HookCodecs.given
import io.github.kapunga.claude.sdk.codec.PermissionCodecs.given
import io.github.kapunga.claude.sdk.errors.*
import io.github.kapunga.claude.sdk.transport.Transport
import io.github.kapunga.claude.sdk.types.*

/** Configuration for building a Query instance. */
final case class QueryConfig(
  canUseTool: Option[CanUseTool] = None,
  hooks: Option[Map[String, List[HookMatcherInternal]]] = None,
  agents: Option[Map[String, JsonObject]] = None,
  initializeTimeout: FiniteDuration = 60.seconds,
)

/** Internal representation of a hook matcher (with callback IDs assigned). */
final case class HookMatcherInternal(
  matcher: Option[String],
  hookCallbackIds: List[String],
  timeout: Option[Double],
)

/**
 * Handles bidirectional control protocol on top of Transport.
 *
 * This class manages:
 * - Control request/response routing
 * - Hook callbacks
 * - Tool permission callbacks
 * - Message streaming
 * - Initialization handshake
 */
final class Query private (
  transport: Transport,
  config: QueryConfig,
  pendingResponses: Ref[IO, Map[String, Deferred[IO, Either[Throwable, JsonObject]]]],
  hookCallbacks: Ref[IO, Map[String, HookCallback]],
  requestCounter: Ref[IO, Int],
  messageQueue: Queue[IO, Option[Either[Throwable, JsonObject]]],
  closedRef: Ref[IO, Boolean],
  initResultRef: Ref[IO, Option[JsonObject]],
  firstResultDeferred: Deferred[IO, Unit],
  supervisor: Supervisor[IO],
):

  private val hookInputDecoder: io.circe.Decoder[HookInput] = summon[io.circe.Decoder[HookInput]]

  /** Initialize the control protocol. Sends initialize request and waits for response. */
  def initialize: IO[Option[JsonObject]] =
    for
      _ <- hookCallbacks.get
      hooksConfig = config.hooks.map { hooks =>
        val fields = hooks.map { case (event, matchers) =>
          event -> Json.arr(matchers.map { m =>
            val f = List.newBuilder[(String, Json)]
            f += ("matcher" -> m.matcher.asJson)
            f += ("hookCallbackIds" -> m.hookCallbackIds.asJson)
            m.timeout.foreach(t => f += ("timeout" -> t.asJson))
            Json.fromFields(f.result())
          }*)
        }
        JsonObject.fromMap(fields)
      }
      request = {
        val f = List.newBuilder[(String, Json)]
        f += ("subtype" -> "initialize".asJson)
        f += ("hooks" -> hooksConfig.asJson)
        config.agents.foreach { agents =>
          f += ("agents" -> Json.fromJsonObject(
            JsonObject.fromMap(agents.view.mapValues(Json.fromJsonObject).toMap)
          ))
        }
        JsonObject.fromIterable(f.result())
      }
      response <- sendControlRequest(request, config.initializeTimeout)
      _ <- initResultRef.set(Some(response))
    yield Some(response)

  /** Get the initialization result. */
  def initializationResult: IO[Option[JsonObject]] = initResultRef.get

  /** Receive SDK messages as a stream (not control messages). */
  def receiveMessages: Stream[IO, JsonObject] =
    Stream.fromQueueNoneTerminated(messageQueue).rethrow

  /** Send interrupt control request. */
  def interrupt: IO[Unit] =
    sendControlRequest(JsonObject("subtype" -> "interrupt".asJson), 60.seconds).void

  /** Change permission mode. */
  def setPermissionMode(mode: PermissionMode): IO[Unit] =
    sendControlRequest(
      JsonObject("subtype" -> "set_permission_mode".asJson, "mode" -> mode.wireValue.asJson),
      60.seconds,
    ).void

  /** Change the AI model. */
  def setModel(model: Option[String]): IO[Unit] =
    sendControlRequest(
      JsonObject("subtype" -> "set_model".asJson, "model" -> model.asJson),
      60.seconds,
    ).void

  /** Rewind tracked files to a specific user message. */
  def rewindFiles(userMessageId: String): IO[Unit] =
    sendControlRequest(
      JsonObject("subtype" -> "rewind_files".asJson, "user_message_id" -> userMessageId.asJson),
      60.seconds,
    ).void

  /** Get MCP server status. */
  def getMcpStatus: IO[JsonObject] =
    sendControlRequest(JsonObject("subtype" -> "mcp_status".asJson), 60.seconds)

  /** Stream input messages to the transport. */
  def streamInput(messages: Stream[IO, JsonObject]): IO[Unit] =
    messages
      .evalMap { msg =>
        closedRef.get.flatMap {
          case true => IO.unit
          case false => transport.write(Json.fromJsonObject(msg).noSpaces + "\n")
        }
      }
      .compile
      .drain >>
      // If we have hooks, wait for first result before ending input
      (if config.hooks.exists(_.nonEmpty) then
         firstResultDeferred.get.timeoutTo(60.seconds, IO.unit) >> transport.endInput
       else transport.endInput)

  /** Process incoming messages from the transport, routing control vs SDK messages. */
  def processMessages: Pipe[IO, JsonObject, Nothing] =
    _.evalMap(routeMessage).drain ++ Stream.exec(messageQueue.offer(None))

  /** Close the query. */
  def close: IO[Unit] =
    closedRef.set(true) >>
      messageQueue.offer(None) >>
      transport.endInput

  // --- Private implementation ---

  private def routeMessage(msg: JsonObject): IO[Unit] =
    val msgType = msg("type").flatMap(_.asString)
    msgType match
      case Some("control_response") =>
        handleControlResponse(msg)
      case Some("control_request") =>
        handleIncomingControlRequest(msg)
      case Some("control_cancel_request") =>
        IO.unit // TODO: cancellation support
      case _ =>
        // Track result messages for stream closure
        val trackResult =
          if msgType.contains("result") then
            firstResultDeferred.complete(()).void.handleError(_ => ())
          else IO.unit
        trackResult >> messageQueue.offer(Some(Right(msg)))

  private def handleControlResponse(msg: JsonObject): IO[Unit] =
    val responseObj = msg("response").flatMap(_.asObject)
    responseObj match
      case None => IO.unit
      case Some(response) =>
        val requestId = response("request_id").flatMap(_.asString)
        requestId match
          case None => IO.unit
          case Some(id) =>
            pendingResponses.modify { pending =>
              pending.get(id) match
                case None => (pending, IO.unit)
                case Some(deferred) =>
                  val isError = response("subtype").flatMap(_.asString).contains("error")
                  val result =
                    if isError then
                      val err = response("error").flatMap(_.asString).getOrElse("Unknown error")
                      Left(new ControlProtocolError(err))
                    else Right(response("response").flatMap(_.asObject).getOrElse(JsonObject.empty))
                  (pending - id, deferred.complete(result).void)
            }.flatten

  private def handleIncomingControlRequest(msg: JsonObject): IO[Unit] =
    val requestId = msg("request_id").flatMap(_.asString).getOrElse("")
    val requestData = msg("request").flatMap(_.asObject).getOrElse(JsonObject.empty)
    val subtype = requestData("subtype").flatMap(_.asString).getOrElse("")

    supervisor
      .supervise(
        handleControlRequestImpl(requestId, subtype, requestData)
          .handleErrorWith { e =>
            sendControlResponse(requestId, Left(e.getMessage))
          }
      )
      .void

  private def handleControlRequestImpl(
    requestId: String,
    subtype: String,
    request: JsonObject,
  ): IO[Unit] =
    subtype match
      case "can_use_tool" =>
        config.canUseTool match
          case None => sendControlResponse(requestId, Left("canUseTool callback is not provided"))
          case Some(canUseTool) =>
            val toolName = request("tool_name").flatMap(_.asString).getOrElse("")
            val input = request("input").flatMap(_.asObject).getOrElse(JsonObject.empty)
            val context = ToolPermissionContext(suggestions = Nil)
            canUseTool(toolName, input, context).flatMap { result =>
              val responseJson = io.circe
                .Encoder[PermissionResult]
                .apply(result)
                .asObject
                .getOrElse(JsonObject.empty)
              // For allow, include original input if no updated input
              val finalResponse = result match
                case PermissionResult.Allow(r) if r.updatedInput.isEmpty =>
                  responseJson.add("updatedInput", Json.fromJsonObject(input))
                case _ => responseJson
              sendControlResponse(requestId, Right(finalResponse))
            }

      case "hook_callback" =>
        val callbackId = request("callback_id").flatMap(_.asString).getOrElse("")
        val hookInputJson = request("input").getOrElse(Json.Null)
        val toolUseId = request("tool_use_id").flatMap(_.asString)
        hookCallbacks.get.flatMap { callbacks =>
          callbacks.get(callbackId) match
            case None =>
              sendControlResponse(requestId, Left(s"No hook callback found for ID: $callbackId"))
            case Some(callback) =>
              // Parse hook input and invoke callback
              hookInputJson.as[HookInput](hookInputDecoder) match
                case Left(err) =>
                  sendControlResponse(
                    requestId,
                    Left(s"Failed to parse hook input: ${err.message}"),
                  )
                case Right(parsedInput) =>
                  val ctx = HookContext()
                  callback(parsedInput, toolUseId, ctx).flatMap { output =>
                    val outputJson = io.circe
                      .Encoder[HookJsonOutput]
                      .apply(output)
                      .asObject
                      .getOrElse(JsonObject.empty)
                    sendControlResponse(requestId, Right(outputJson))
                  }
        }

      case "mcp_message" =>
        sendControlResponse(requestId, Left("SDK MCP servers not yet supported in Scala SDK"))

      case other =>
        sendControlResponse(requestId, Left(s"Unsupported control request subtype: $other"))

  private def sendControlRequest(
    request: JsonObject,
    timeout: FiniteDuration,
  ): IO[JsonObject] =
    for
      counter <- requestCounter.updateAndGet(_ + 1)
      randomHex <- IO(java.util.UUID.randomUUID().toString.take(8))
      requestId = s"req_${counter}_$randomHex"
      deferred <- Deferred[IO, Either[Throwable, JsonObject]]
      _ <- pendingResponses.update(_ + (requestId -> deferred))
      json = ControlCodecs.encodeControlRequest(requestId, request)
      _ <- transport.write(json.noSpaces + "\n")
      result <- deferred.get.timeoutTo(
        timeout,
        pendingResponses.update(_ - requestId) >>
          IO.raiseError(
            new ControlProtocolError(
              s"Control request timeout: ${request("subtype").flatMap(_.asString).getOrElse("unknown")}"
            )
          ),
      )
      _ <- pendingResponses.update(_ - requestId)
      value <- IO.fromEither(result)
    yield value

  private def sendControlResponse(requestId: String, result: Either[String, JsonObject]): IO[Unit] =
    val response = result match
      case Right(data) =>
        SDKControlResponse(
          ControlResponseData.Success(ControlSuccessResponse(requestId, Some(data)))
        )
      case Left(error) =>
        SDKControlResponse(ControlResponseData.Error(ControlErrorResponse(requestId, error)))
    transport.write(io.circe.Encoder[SDKControlResponse].apply(response).noSpaces + "\n")

object Query:

  /** Create a Query instance. Supervisor is allocated as a Resource internally. */
  def make(
    transport: Transport,
    config: QueryConfig,
  ): IO[Query] =
    for
      pendingResponses <- Ref.of[IO, Map[String, Deferred[IO, Either[Throwable, JsonObject]]]](
        Map.empty
      )
      hookCallbacksRef <- Ref.of[IO, Map[String, HookCallback]](Map.empty)
      requestCounter <- Ref.of[IO, Int](0)
      messageQueue <- Queue.unbounded[IO, Option[Either[Throwable, JsonObject]]]
      closedRef <- Ref.of[IO, Boolean](false)
      initResultRef <- Ref.of[IO, Option[JsonObject]](None)
      firstResultDeferred <- Deferred[IO, Unit]
      supervisor <- Supervisor[IO](await = false).allocated.map(_._1)
    yield new Query(
      transport,
      config,
      pendingResponses,
      hookCallbacksRef,
      requestCounter,
      messageQueue,
      closedRef,
      initResultRef,
      firstResultDeferred,
      supervisor,
    )

  /** Create a Query instance as a Resource with proper cleanup. */
  def resource(
    transport: Transport,
    config: QueryConfig,
  ): Resource[IO, Query] =
    for
      pendingResponses <- Resource.eval(
        Ref.of[IO, Map[String, Deferred[IO, Either[Throwable, JsonObject]]]](Map.empty)
      )
      hookCallbacksRef <- Resource.eval(Ref.of[IO, Map[String, HookCallback]](Map.empty))
      requestCounter <- Resource.eval(Ref.of[IO, Int](0))
      messageQueue <- Resource.eval(Queue.unbounded[IO, Option[Either[Throwable, JsonObject]]])
      closedRef <- Resource.eval(Ref.of[IO, Boolean](false))
      initResultRef <- Resource.eval(Ref.of[IO, Option[JsonObject]](None))
      firstResultDeferred <- Resource.eval(Deferred[IO, Unit])
      supervisor <- Supervisor[IO](await = false)
    yield new Query(
      transport,
      config,
      pendingResponses,
      hookCallbacksRef,
      requestCounter,
      messageQueue,
      closedRef,
      initResultRef,
      firstResultDeferred,
      supervisor,
    )

  /** Convert external hook configuration to internal format with callback IDs. */
  def convertHooks(
    hooks: Map[HookEvent, List[HookMatcher]],
    hookCallbacksRef: Ref[IO, Map[String, HookCallback]],
  ): IO[Map[String, List[HookMatcherInternal]]] =
    hooks.toList
      .traverse { case (event, matchers) =>
        matchers
          .traverse { matcher =>
            matcher.hooks
              .traverse { callback =>
                for id <- hookCallbacksRef.modify { cbs =>
                    val id = s"hook_${cbs.size}"
                    (cbs + (id -> callback), id)
                  }
                yield id
              }
              .map { ids =>
                HookMatcherInternal(matcher.matcher, ids, matcher.timeout)
              }
          }
          .map(event.wireValue -> _)
      }
      .map(_.toMap)
