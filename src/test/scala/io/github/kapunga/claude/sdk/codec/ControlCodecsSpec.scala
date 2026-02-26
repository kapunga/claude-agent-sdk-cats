package io.github.kapunga.claude.sdk.codec

import io.circe.*
import io.circe.parser.*
import io.circe.syntax.*

import munit.CatsEffectSuite

import io.github.kapunga.claude.sdk.types.*

class ControlCodecsSpec extends CatsEffectSuite:

  import ControlCodecs.given

  test("decode can_use_tool control request") {
    val json = parse("""{
      "request_id": "req_1",
      "request": {
        "subtype": "can_use_tool",
        "tool_name": "Bash",
        "input": {"command": "rm -rf /"},
        "permission_suggestions": null,
        "blocked_path": null
      }
    }""").toOption.get
    val result = json.as[SDKControlRequest]
    assert(result.isRight)
    result.foreach { req =>
      assertEquals(req.requestId, "req_1")
      assert(req.request.isInstanceOf[SDKControlPermissionRequest])
      val perm = req.request.asInstanceOf[SDKControlPermissionRequest]
      assertEquals(perm.toolName, "Bash")
    }
  }

  test("decode hook_callback control request") {
    val json = parse("""{
      "request_id": "req_2",
      "request": {
        "subtype": "hook_callback",
        "callback_id": "hook_0",
        "input": {"hook_event_name": "PreToolUse", "session_id": "s", "transcript_path": "t", "cwd": "/", "tool_name": "Bash", "tool_input": {}, "tool_use_id": "tu1"},
        "tool_use_id": "tu1"
      }
    }""").toOption.get
    val result = json.as[SDKControlRequest]
    assert(result.isRight)
    result.foreach { req =>
      assertEquals(req.requestId, "req_2")
      assert(req.request.isInstanceOf[SDKHookCallbackRequest])
    }
  }

  test("decode initialize control request") {
    val json = parse("""{
      "request_id": "req_3",
      "request": {
        "subtype": "initialize",
        "hooks": null
      }
    }""").toOption.get
    val result = json.as[SDKControlRequest]
    assert(result.isRight)
  }

  test("encode success control response") {
    val response = SDKControlResponse(
      ControlResponseData.Success(
        ControlSuccessResponse("req_1", Some(JsonObject("status" -> "ok".asJson)))
      )
    )
    val json = response.asJson
    val typeField = json.hcursor.downField("type").as[String]
    assertEquals(typeField, Right("control_response"))
    val subtype = json.hcursor.downField("response").downField("subtype").as[String]
    assertEquals(subtype, Right("success"))
  }

  test("encode error control response") {
    val response = SDKControlResponse(
      ControlResponseData.Error(
        ControlErrorResponse("req_1", "something went wrong")
      )
    )
    val json = response.asJson
    val subtype = json.hcursor.downField("response").downField("subtype").as[String]
    assertEquals(subtype, Right("error"))
    val error = json.hcursor.downField("response").downField("error").as[String]
    assertEquals(error, Right("something went wrong"))
  }

  test("encodeControlRequest produces correct structure") {
    val request = JsonObject("subtype" -> "interrupt".asJson)
    val json = ControlCodecs.encodeControlRequest("req_42", request)
    val typeField = json.hcursor.downField("type").as[String]
    assertEquals(typeField, Right("control_request"))
    val id = json.hcursor.downField("request_id").as[String]
    assertEquals(id, Right("req_42"))
  }

  test("decode control response data - success") {
    val json = parse("""{
      "subtype": "success",
      "request_id": "req_1",
      "response": {"commands": []}
    }""").toOption.get
    val result = json.as[ControlResponseData]
    assert(result.isRight)
    result.foreach {
      case ControlResponseData.Success(r) =>
        assertEquals(r.requestId, "req_1")
        assert(r.response.isDefined)
      case _ => fail("Expected Success")
    }
  }

  test("decode control response data - error") {
    val json = parse("""{
      "subtype": "error",
      "request_id": "req_1",
      "error": "timeout"
    }""").toOption.get
    val result = json.as[ControlResponseData]
    assert(result.isRight)
    result.foreach {
      case ControlResponseData.Error(r) =>
        assertEquals(r.requestId, "req_1")
        assertEquals(r.error, "timeout")
      case _ => fail("Expected Error")
    }
  }
