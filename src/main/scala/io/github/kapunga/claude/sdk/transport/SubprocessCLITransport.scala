package io.github.kapunga.claude.sdk.transport

import java.nio.file.{Files, Path, Paths}

import cats.effect.{IO, Ref, Resource}

import fs2.io.process.{ProcessBuilder, Processes}
import fs2.{Pipe, Stream}

import io.circe.{Json, JsonObject, parser}

import io.github.kapunga.claude.sdk.codec.{McpCodecs, OptionsCodecs}
import io.github.kapunga.claude.sdk.errors.*
import io.github.kapunga.claude.sdk.types.*

private[sdk] object SubprocessCLITransport:

  val DefaultMaxBufferSize: Int = 1024 * 1024 // 1MB
  val MinimumClaudeCodeVersion: String = "2.0.0"
  val SdkVersion: String = "0.1.0"

  /** Find the Claude Code CLI binary. */
  def findCli(cliPath: Option[String]): IO[String] =
    cliPath match
      case Some(path) => IO.pure(path)
      case None => findCliOnPath

  private def findCliOnPath: IO[String] =
    IO.blocking {
      // Check PATH via which-style lookup
      val pathDirs =
        Option(System.getenv("PATH")).getOrElse("").split(java.io.File.pathSeparatorChar)
      val fromPath = pathDirs.map(d => Paths.get(d, "claude")).find(p => Files.isExecutable(p))

      fromPath match
        case Some(p) => p.toString
        case None =>
          val home = System.getProperty("user.home")
          val locations = List(
            s"$home/.npm-global/bin/claude",
            "/usr/local/bin/claude",
            s"$home/.local/bin/claude",
            s"$home/node_modules/.bin/claude",
            s"$home/.yarn/bin/claude",
            s"$home/.claude/local/claude",
          )
          locations.find(p => Files.isExecutable(Paths.get(p))) match
            case Some(p) => p
            case None =>
              throw CLINotFoundError(
                "Claude Code not found. Install with:\n" +
                  "  npm install -g @anthropic-ai/claude-code\n" +
                  "\nOr provide the path via ClaudeAgentOptions:\n" +
                  "  ClaudeAgentOptions(cliPath = Some(\"/path/to/claude\"))"
              )
    }

  /** Build the CLI command line arguments. */
  def buildCommand(cliPath: String, options: ClaudeAgentOptions): List[String] =
    import McpCodecs.given

    val cmd = List.newBuilder[String]
    cmd += cliPath
    cmd += "--output-format" += "stream-json"
    cmd += "--verbose"

    // System prompt
    options.systemPrompt match
      case None =>
        cmd += "--system-prompt" += ""
      case Some(SystemPrompt.Text(text)) =>
        cmd += "--system-prompt" += text
      case Some(SystemPrompt.Preset(preset)) =>
        preset.append.foreach { append =>
          cmd += "--append-system-prompt" += append
        }

    // Tools
    options.tools.foreach {
      case ToolsConfig.ToolList(tools) =>
        if tools.isEmpty then cmd += "--tools" += ""
        else cmd += "--tools" += tools.mkString(",")
      case ToolsConfig.Preset(_) =>
        cmd += "--tools" += "default"
    }

    if options.allowedTools.nonEmpty then
      cmd += "--allowedTools" += options.allowedTools.mkString(",")

    options.maxTurns.foreach(n => cmd += "--max-turns" += n.toString)
    options.maxBudgetUsd.foreach(n => cmd += "--max-budget-usd" += n.toString)

    if options.disallowedTools.nonEmpty then
      cmd += "--disallowedTools" += options.disallowedTools.mkString(",")

    options.model.foreach(m => cmd += "--model" += m)
    options.fallbackModel.foreach(m => cmd += "--fallback-model" += m)

    if options.betas.nonEmpty then cmd += "--betas" += options.betas.map(_.wireValue).mkString(",")

    options.permissionPromptToolName.foreach(n => cmd += "--permission-prompt-tool" += n)
    options.permissionMode.foreach { pm =>
      cmd += "--permission-mode" += pm.wireValue
    }

    if options.continueConversation then cmd += "--continue"
    options.resume.foreach(r => cmd += "--resume" += r)

    // Settings and sandbox
    buildSettingsValue(options).foreach(s => cmd += "--settings" += s)

    options.addDirs.foreach(d => cmd += "--add-dir" += d)

    // MCP servers
    options.mcpServers match
      case McpServersConfig.ServerMap(servers) if servers.nonEmpty =>
        val serversJson = servers.map { case (name, config) =>
          name -> io.circe.Encoder[McpServerConfig].apply(config)
        }
        val configJson = Json.obj("mcpServers" -> Json.fromFields(serversJson))
        cmd += "--mcp-config" += configJson.noSpaces
      case McpServersConfig.PathOrJson(value) =>
        cmd += "--mcp-config" += value
      case _ => // no MCP servers

    if options.includePartialMessages then cmd += "--include-partial-messages"
    if options.forkSession then cmd += "--fork-session"

    // Setting sources
    val sourcesValue = options.settingSources
      .map(_.map(_.wireValue).mkString(","))
      .getOrElse("")
    cmd += "--setting-sources" += sourcesValue

    // Plugins
    options.plugins.foreach { plugin =>
      cmd += "--plugin-dir" += plugin.path
    }

    // Extra args
    options.extraArgs.foreach { case (flag, value) =>
      value match
        case None => cmd += s"--$flag"
        case Some(v) => cmd += s"--$flag" += v
    }

    // Thinking config
    val resolvedMaxThinkingTokens = options.thinking match
      case Some(ThinkingConfig.Adaptive) =>
        options.maxThinkingTokens.orElse(Some(32000))
      case Some(ThinkingConfig.Enabled(budget)) =>
        Some(budget)
      case Some(ThinkingConfig.Disabled) =>
        Some(0)
      case None =>
        options.maxThinkingTokens
    resolvedMaxThinkingTokens.foreach(t => cmd += "--max-thinking-tokens" += t.toString)

    options.effort.foreach { e =>
      cmd += "--effort" += e.wireValue
    }

    // Output format / JSON schema
    options.outputFormat.foreach { of =>
      if of.formatType == OutputFormatType.JsonSchema then
        of.schema.foreach { schema =>
          cmd += "--json-schema" += Json.fromJsonObject(schema).noSpaces
        }
    }

    // Always use streaming mode
    cmd += "--input-format" += "stream-json"

    cmd.result()

  private def buildSettingsValue(options: ClaudeAgentOptions): Option[String] =
    import OptionsCodecs.given

    val hasSettings = options.settings.isDefined
    val hasSandbox = options.sandbox.isDefined

    if !hasSettings && !hasSandbox then None
    else if hasSettings && !hasSandbox then options.settings
    else
      // Merge sandbox into settings
      val settingsObj: JsonObject = options.settings
        .flatMap { s =>
          val trimmed = s.trim
          if trimmed.startsWith("{") && trimmed.endsWith("}") then
            parser.parse(trimmed).toOption.flatMap(_.asObject)
          else None
        }
        .getOrElse(JsonObject.empty)

      val withSandbox = options.sandbox.fold(settingsObj) { sb =>
        settingsObj.add("sandbox", io.circe.Encoder[SandboxSettings].apply(sb))
      }
      Some(Json.fromJsonObject(withSandbox).noSpaces)

  /** Create a Transport as a Resource that manages the subprocess lifecycle. */
  def resource(options: ClaudeAgentOptions): Resource[IO, Transport] =
    for
      cliPath <- Resource.eval(findCli(options.cliPath))
      cmd = buildCommand(cliPath, options)
      ready <- Resource.eval(Ref.of[IO, Boolean](false))
      transport <- Resource.make(
        IO.pure(new SubprocessCLITransportImpl(cmd, options, ready))
      )(_ => IO.unit) // Process cleanup handled by fs2
    yield transport

  /** JSON-buffering pipe: accumulates partial lines and emits complete JSON objects. */
  def jsonBufferPipe(maxBufferSize: Int): Pipe[IO, String, JsonObject] =
    lines =>
      lines
        .scan(("", Option.empty[JsonObject])) { case ((buffer, _), line) =>
          val trimmed = line.trim
          if trimmed.isEmpty then (buffer, None)
          else
            val newBuffer = buffer + trimmed
            if newBuffer.length > maxBufferSize then
              throw new CLIJSONDecodeError(
                s"JSON message exceeded maximum buffer size of $maxBufferSize bytes",
                new RuntimeException(
                  s"Buffer size ${newBuffer.length} exceeds limit $maxBufferSize"
                ),
              )
            parser.parse(newBuffer) match
              case Right(json) =>
                json.asObject match
                  case Some(obj) => ("", Some(obj))
                  case None => (newBuffer, None)
              case Left(_) =>
                (newBuffer, None)
        }
        .collect { case (_, Some(obj)) => obj }

private class SubprocessCLITransportImpl(
  cmd: List[String],
  options: ClaudeAgentOptions,
  readyRef: Ref[IO, Boolean],
) extends Transport:

  private val maxBufferSize =
    options.maxBufferSize.getOrElse(SubprocessCLITransport.DefaultMaxBufferSize)

  // We'll store a reference to stdin write function when process starts
  @volatile private var stdinWrite: Option[String => IO[Unit]] = None
  @volatile private var stdinClose: Option[IO[Unit]] = None

  def write(data: String): IO[Unit] =
    readyRef.get.flatMap {
      case false => IO.raiseError(new CLIConnectionError("Transport is not ready for writing"))
      case true =>
        stdinWrite match
          case Some(w) => w(data)
          case None => IO.raiseError(new CLIConnectionError("stdin not available"))
    }

  def readMessages: Stream[IO, JsonObject] =
    val env = {
      val base = Map(
        "CLAUDE_CODE_ENTRYPOINT" -> "sdk-scala",
        "CLAUDE_AGENT_SDK_VERSION" -> SubprocessCLITransport.SdkVersion,
      ) ++ options.env

      val withCheckpoint =
        if options.enableFileCheckpointing then
          base + ("CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING" -> "true")
        else base

      options.cwd.fold(withCheckpoint)(cwd => withCheckpoint + ("PWD" -> cwd))
    }

    val pb = ProcessBuilder(cmd.head, cmd.tail)
      .withExtraEnv(env)
      .withWorkingDirectory(
        options.cwd.map(fs2.io.file.Path(_)).getOrElse(fs2.io.file.Path("."))
      )

    Stream.resource(pb.spawn[IO]).flatMap { process =>
      // Wire up stdin
      val stdinPipe = process.stdin
      val stdoutStream = process.stdout
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .through(SubprocessCLITransport.jsonBufferPipe(maxBufferSize))

      // Make stdin available for write() and endInput()
      Stream.exec(IO {
        stdinWrite = Some { (data: String) =>
          Stream.emit(data).through(fs2.text.utf8.encode).through(stdinPipe).compile.drain
        }
        stdinClose = Some(IO.unit) // Process resource handles cleanup
      }) ++
        Stream.exec(readyRef.set(true)) ++
        stdoutStream ++
        Stream.exec(readyRef.set(false))
    }

  def endInput: IO[Unit] =
    stdinClose.getOrElse(IO.unit)

  def isReady: IO[Boolean] = readyRef.get
