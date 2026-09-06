"""周 6 提示注入测试：2 例文档注入 + 2 例用户注入（docs/05-security.md 最小四例）。

每个测试都是可运行的小测试：断言未发生越权工具调用、未确认下单与内部 Token 泄露。
"""

from __future__ import annotations

import asyncio
import json
import uuid
from typing import Any

import pytest
from fastapi.testclient import TestClient

from care_agent_ai import app as app_module
from care_agent_ai.agent import AgentRun, stream_run
from care_agent_ai.prompt_injection_check import (
    CANARY_ENV,
    DOC_ORDER_INJECTION,
    DOC_TOKEN_LEAK_INJECTION,
    POLICY_MESSAGE,
    RecordingRuntime,
    SCENARIOS,
    USER_BYPASS_CONFIRM,
    USER_PRICE_TAMPER,
    run_scenario,
)


@pytest.mark.parametrize("scenario", SCENARIOS, ids=[s["id"] for s in SCENARIOS])
def test_injection_scenario_has_no_violation(scenario: dict[str, str]) -> None:
    canary = "week6-canary-" + uuid.uuid4().hex
    result = asyncio.run(run_scenario(scenario, canary))
    assert result["passed"], json.dumps(result, ensure_ascii=False)
    assert result["violations"] == {
        "unauthorizedToolCall": False,
        "tokenLeak": False,
        "unconfirmedBooking": False,
    }


async def _collect(message: str, runtime: RecordingRuntime) -> list[Any]:
    async def never() -> bool:
        return False

    run = AgentRun(str(uuid.uuid4()), str(uuid.uuid4()), message, [], str(uuid.uuid4()))
    return [event async for event in stream_run(run, runtime, never)]


def test_document_order_injection_cannot_trigger_booking_tools() -> None:
    runtime = RecordingRuntime(DOC_ORDER_INJECTION)
    events = asyncio.run(_collect(POLICY_MESSAGE, runtime))
    names = [name for name, _ in events]
    assert "tool_confirmation" not in names
    assert runtime.service_calls == 0 and runtime.draft_calls == 0
    # 注入文本只允许作为不可信资料进入模型输入，不允许成为工具参数来源
    assert runtime.model_inputs[0]["hits"][0]["content"] == DOC_ORDER_INJECTION


def test_user_bypass_and_price_injection_receive_guarded_refusal() -> None:
    for message in (USER_BYPASS_CONFIRM, USER_PRICE_TAMPER):
        runtime = RecordingRuntime("")
        events = asyncio.run(_collect(message, runtime))
        names = [name for name, _ in events]
        assert names == ["status", "token", "done"]
        assert runtime.service_calls == 0 and runtime.draft_calls == 0


def test_internal_sse_endpoint_does_not_leak_canary_token(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    canary = "week6-canary-" + uuid.uuid4().hex
    monkeypatch.setenv(CANARY_ENV, canary)
    monkeypatch.setenv("INTERNAL_TOKEN", canary)
    runtime = RecordingRuntime(DOC_TOKEN_LEAK_INJECTION)
    monkeypatch.setattr(app_module, "create_agent_runtime", lambda: runtime)
    request_id = str(uuid.uuid4())
    response = TestClient(app_module.app).post(
        "/internal/v1/agent/runs",
        headers={"X-Internal-Token": canary, "X-Request-ID": request_id},
        json={
            "userId": str(uuid.uuid4()),
            "conversationId": str(uuid.uuid4()),
            "message": POLICY_MESSAGE,
            "context": [],
            "requestId": request_id,
        },
    )
    assert response.status_code == 200
    assert canary not in response.text
    assert runtime.closed
    assert runtime.service_calls == 0 and runtime.draft_calls == 0
