"""CareAgent 周 2 运行时配置。

所有值都从环境变量读取；内部 Token 不提供默认值（未配置时内部接口一律拒绝），
数据库 DSN 仅给出本机演示默认值，生产必须覆盖。
"""

from __future__ import annotations

import hmac
import os

# 仅本机演示默认值；与 compose.yaml 的本地开发口令一致，生产必须覆盖。
DEFAULT_RAG_DATABASE_URL = "postgresql://careagent:careagent_local_dev@localhost:5432/careagent"


def rag_database_url() -> str:
    return os.environ.get("RAG_DATABASE_URL", DEFAULT_RAG_DATABASE_URL)


def token_is_valid(provided: str | None) -> bool:
    """常量时间比较内部 Token；未配置或缺失时一律拒绝。

    每次调用都读取环境变量，方便测试注入不同 Token。
    """
    expected = os.environ.get("INTERNAL_TOKEN", "")
    if not expected or not provided:
        return False
    return hmac.compare_digest(provided, expected)
