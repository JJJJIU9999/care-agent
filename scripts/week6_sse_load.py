"""CareAgent 周 6：20 路 SSE 与断连测试。

同时建立 20 条经 Nginx→Java→Python 的公开 SSE 流（12 条服务型 + 8 条政策型），
记录建立/完成/失败/超时数量；用固定随机种子（42）选取 4 条服务型与 2 条政策型
客户端主动中途断开，输出各断开的 Request ID 供 Java/Python 日志核对。
失败事件区分供应商错误码（MODEL_UNAVAILABLE 等）与本系统错误码。

只描述为“20 路流式连接稳定性与断连取消测试”，不构成生产并发承诺。

运行：先启动完整 Compose 栈，再执行
    uv run --offline python scripts/week6_sse_load.py
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import random
import time
import uuid
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import httpx

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUTPUT = ROOT / "data/evaluation/week6_sse_results.json"

BASE_URL = os.environ.get("CAREAGENT_DEMO_BASE_URL", "http://127.0.0.1:8088")
DEMO_USER = os.environ.get("CAREAGENT_DEMO_USERNAME", "demo_user")
DEMO_PASSWORD = os.environ.get("CAREAGENT_DEMO_PASSWORD", "demo-user-2026")

SERVICE_MESSAGE = "武侯区有哪些助洁服务？"
POLICY_MESSAGE = "四川省高龄津贴的申请条件是什么？"

VENDOR_ERROR_CODES = {"MODEL_UNAVAILABLE"}


async def login(client: httpx.AsyncClient) -> str:
    response = await client.post(
        f"{BASE_URL}/api/v1/auth/login",
        json={"username": DEMO_USER, "password": DEMO_PASSWORD},
    )
    response.raise_for_status()
    return response.json()["accessToken"]


async def create_conversation(client: httpx.AsyncClient, token: str) -> str:
    response = await client.post(
        f"{BASE_URL}/api/v1/conversations",
        headers={"Authorization": f"Bearer {token}"},
        json={},
    )
    response.raise_for_status()
    return response.json()["conversationId"]


async def run_stream(
    client: httpx.AsyncClient,
    token: str,
    conversation_id: str,
    kind: str,
    message: str,
    deadline: float,
    interrupt: bool,
) -> dict[str, Any]:
    request_id = str(uuid.uuid4())
    record: dict[str, Any] = {
        "requestId": request_id,
        "kind": kind,
        "interruptPlanned": interrupt,
        "established": False,
        "result": "failed",
        "eventCounts": {},
        "terminalErrorCode": None,
    }
    started = time.perf_counter()
    try:
        async with client.stream(
            "POST",
            f"{BASE_URL}/api/v1/conversations/{conversation_id}/messages",
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
                "Accept": "text/event-stream",
                "X-Request-ID": request_id,
            },
            json={"message": message, "context": []},
            timeout=httpx.Timeout(deadline - started + 5, connect=5),
        ) as response:
            if response.status_code != 200:
                record["terminalErrorCode"] = f"HTTP_{response.status_code}"
                return record
            record["established"] = True
            events: list[str] = []
            current_event: str | None = None
            async for line in response.aiter_lines():
                if line.startswith("event:"):
                    current_event = line[6:].strip()
                    events.append(current_event)
                    counts = record["eventCounts"]
                    counts[current_event] = counts.get(current_event, 0) + 1
                elif line.startswith("data:") and current_event == "error":
                    try:
                        record["terminalErrorCode"] = json.loads(line[5:].strip()).get("code")
                    except json.JSONDecodeError:
                        record["terminalErrorCode"] = "UNPARSEABLE_ERROR_EVENT"
                if interrupt and len(events) >= 3:
                    # 主动断开：在第 3 个事件后关闭连接模拟浏览器停止，
                    # 后端取消由脚本外按 Request ID 核对 Java/Python 日志
                    record["result"] = "interrupted"
                    record["interruptedAfterEvent"] = len(events)
                    record["durationMs"] = round((time.perf_counter() - started) * 1000, 1)
                    return record
                if current_event in ("done", "error"):
                    break
            last = events[-1] if events else None
            if last == "done":
                record["result"] = "completed"
            elif last != "error":
                record["result"] = "timeout"
            record["durationMs"] = round((time.perf_counter() - started) * 1000, 1)
            return record
    except httpx.TimeoutException as exception:
        record["result"] = "timeout"
        record["terminalErrorCode"] = type(exception).__name__
        record["durationMs"] = round((time.perf_counter() - started) * 1000, 1)
        return record
    except httpx.HTTPError as exception:
        record["terminalErrorCode"] = type(exception).__name__
        record["durationMs"] = round((time.perf_counter() - started) * 1000, 1)
        return record


async def main_async(args: argparse.Namespace) -> dict[str, Any]:
    async with httpx.AsyncClient() as client:
        token = await login(client)
        conversations = await asyncio.gather(
            *(create_conversation(client, token) for _ in range(args.connections))
        )

    kinds = ["policy"] * args.policy + ["service"] * (args.connections - args.policy)
    rng = random.Random(args.seed)
    policy_indices = [index for index, kind in enumerate(kinds) if kind == "policy"]
    service_indices = [index for index, kind in enumerate(kinds) if kind == "service"]
    interrupt_indices = set(
        rng.sample(policy_indices, args.interrupt_policy)
        + rng.sample(service_indices, args.interrupt_service)
    )
    deadline = time.perf_counter() + args.timeout

    async with httpx.AsyncClient() as client:
        tasks = [
            run_stream(
                client,
                token,
                conversations[index],
                kinds[index],
                POLICY_MESSAGE if kinds[index] == "policy" else SERVICE_MESSAGE,
                deadline,
                index in interrupt_indices,
            )
            for index in range(args.connections)
        ]
        results = await asyncio.gather(*tasks)

    return finalize(args, results, sorted(interrupt_indices))


def finalize(args: argparse.Namespace, results: list[dict[str, Any]], interrupt_indices: list[int]) -> dict[str, Any]:
    counts = {"established": 0, "completed": 0, "failed": 0, "timeout": 0, "interrupted": 0}
    for item in results:
        if item["established"]:
            counts["established"] += 1
        counts[item["result"]] += 1
        if item["result"] == "failed" and item.get("terminalErrorCode"):
            item["failureClass"] = (
                "VENDOR" if item["terminalErrorCode"] in VENDOR_ERROR_CODES else "SYSTEM"
            )
    error_codes = sorted({
        str(data)
        for item in results
        for data in (item.get("terminalErrorCode"),)
        if data
    })
    interrupted_ids = [item["requestId"] for item in results if item["result"] == "interrupted"]
    failed_by_kind = {}
    for item in results:
        if item["result"] in ("failed", "timeout"):
            failed_by_kind.setdefault(item["kind"], []).append(item["requestId"])
    return {
        "capturedAt": datetime.now(UTC).isoformat(),
        "command": f"uv run --offline python scripts/week6_sse_load.py --seed {args.seed}",
        "baseUrl": BASE_URL,
        "hardware": "本机 MacBook（Docker Desktop 单容器栈，演示用途）",
        "config": {
            "connections": args.connections,
            "policyStreams": args.policy,
            "serviceStreams": args.connections - args.policy,
            "timeoutSeconds": args.timeout,
            "seed": args.seed,
            "interruptService": args.interrupt_service,
            "interruptPolicy": args.interrupt_policy,
            "interruptedIndices": interrupt_indices,
        },
        "summary": counts,
        "interruptedRequestIds": interrupted_ids,
        "errorCodes": error_codes,
        "failedOrTimeoutByKind": failed_by_kind,
        "streams": results,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="CareAgent 20 路 SSE 与断连测试")
    parser.add_argument("--connections", type=int, default=20)
    parser.add_argument("--policy", type=int, default=8)
    parser.add_argument("--timeout", type=float, default=90.0)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--interrupt-service", type=int, default=4)
    parser.add_argument("--interrupt-policy", type=int, default=2)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    payload = asyncio.run(main_async(args))
    rendered = json.dumps(payload, ensure_ascii=False, indent=2)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered + "\n", encoding="utf-8")
    print(json.dumps({"summary": payload["summary"], "interruptedRequestIds": payload["interruptedRequestIds"], "errorCodes": payload["errorCodes"]}, ensure_ascii=False, indent=2))
    if payload["summary"]["established"] != args.connections:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
