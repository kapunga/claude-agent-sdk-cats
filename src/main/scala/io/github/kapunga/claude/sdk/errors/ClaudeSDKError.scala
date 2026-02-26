package io.github.kapunga.claude.sdk.errors

/** Base exception for all Claude SDK errors. */
abstract class ClaudeSDKError(message: String, cause: Option[Throwable] = None)
    extends RuntimeException(message, cause.orNull)

/** Generic SDK error for control protocol failures. */
class ControlProtocolError(message: String) extends ClaudeSDKError(message)

object ControlProtocolError:
  def apply(message: String): ControlProtocolError = new ControlProtocolError(message)

/** Raised when unable to connect to Claude Code. */
class CLIConnectionError(message: String, cause: Option[Throwable] = None)
    extends ClaudeSDKError(message, cause)

object CLIConnectionError:
  def apply(message: String, cause: Option[Throwable] = None): CLIConnectionError =
    new CLIConnectionError(message, cause)

/** Raised when Claude Code is not found or not installed. */
class CLINotFoundError(message: String, cliPath: Option[String] = None)
    extends CLIConnectionError(
      cliPath.fold(message)(p => s"$message: $p")
    )

object CLINotFoundError:
  def apply(
    message: String = "Claude Code not found",
    cliPath: Option[String] = None,
  ): CLINotFoundError =
    new CLINotFoundError(message, cliPath)

/** Raised when the CLI process fails. */
class ProcessError(
  message: String,
  val exitCode: Option[Int] = None,
  val stderr: Option[String] = None,
) extends ClaudeSDKError({
      val withExit = exitCode.fold(message)(c => s"$message (exit code: $c)")
      stderr.fold(withExit)(s => s"$withExit\nError output: $s")
    })

object ProcessError:
  def apply(
    message: String,
    exitCode: Option[Int] = None,
    stderr: Option[String] = None,
  ): ProcessError = new ProcessError(message, exitCode, stderr)

/** Raised when unable to decode JSON from CLI output. */
class CLIJSONDecodeError(val line: String, val originalError: Throwable)
    extends ClaudeSDKError(s"Failed to decode JSON: ${line.take(100)}...", Some(originalError))

object CLIJSONDecodeError:
  def apply(line: String, originalError: Throwable): CLIJSONDecodeError =
    new CLIJSONDecodeError(line, originalError)

/** Raised when unable to parse a message from CLI output. */
class MessageParseError(message: String, val data: Option[io.circe.JsonObject] = None)
    extends ClaudeSDKError(message)

object MessageParseError:
  def apply(message: String, data: Option[io.circe.JsonObject] = None): MessageParseError =
    new MessageParseError(message, data)
