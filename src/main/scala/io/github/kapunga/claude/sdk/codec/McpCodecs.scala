package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.syntax.*
import io.github.kapunga.claude.sdk.types.*

object McpCodecs:

  given Encoder[McpServerConfig] = Encoder.instance {
    case McpStdioServerConfig(command, args, env) =>
      val fields = List.newBuilder[(String, Json)]
      fields += ("type" -> "stdio".asJson)
      fields += ("command" -> command.asJson)
      args.foreach(a => fields += ("args" -> a.asJson))
      env.foreach(e => fields += ("env" -> e.asJson))
      Json.fromFields(fields.result())

    case McpSSEServerConfig(url, headers) =>
      val fields = List.newBuilder[(String, Json)]
      fields += ("type" -> "sse".asJson)
      fields += ("url" -> url.asJson)
      headers.foreach(h => fields += ("headers" -> h.asJson))
      Json.fromFields(fields.result())

    case McpHttpServerConfig(url, headers) =>
      val fields = List.newBuilder[(String, Json)]
      fields += ("type" -> "http".asJson)
      fields += ("url" -> url.asJson)
      headers.foreach(h => fields += ("headers" -> h.asJson))
      Json.fromFields(fields.result())
  }

  given Decoder[McpServerConfig] = Decoder.instance { c =>
    // "type" is optional for stdio (backwards compatibility)
    c.downField("type").as[Option[String]].flatMap {
      case Some("sse") =>
        for
          url     <- c.downField("url").as[String]
          headers <- c.downField("headers").as[Option[Map[String, String]]]
        yield McpSSEServerConfig(url, headers)

      case Some("http") =>
        for
          url     <- c.downField("url").as[String]
          headers <- c.downField("headers").as[Option[Map[String, String]]]
        yield McpHttpServerConfig(url, headers)

      case Some("stdio") | None =>
        for
          command <- c.downField("command").as[String]
          args    <- c.downField("args").as[Option[List[String]]]
          env     <- c.downField("env").as[Option[Map[String, String]]]
        yield McpStdioServerConfig(command, args, env)

      case Some(other) =>
        Left(DecodingFailure(s"Unknown MCP server type: $other", c.history))
    }
  }

  given Encoder[SdkMcpTool] = Encoder.instance { t =>
    Json.obj(
      "name"        -> t.name.asJson,
      "description" -> t.description.asJson,
      "inputSchema" -> t.inputSchema.asJson,
    )
  }

  given Encoder[SdkPluginConfig] = Encoder.instance { p =>
    Json.obj(
      "type" -> "local".asJson,
      "path" -> p.path.asJson,
    )
  }
