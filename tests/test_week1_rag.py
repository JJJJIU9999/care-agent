from pathlib import Path
from types import SimpleNamespace

import pytest

from care_agent_ai.rag_cli import (
    PolicyChunk,
    SearchHit,
    citation,
    cited_hits,
    decide_behavior,
    generate_answer,
    guardrail_behavior,
    parse_policy_markdown,
)


ROOT = Path(__file__).resolve().parents[1]


def sample_hit(score: float = 0.8) -> SearchHit:
    return SearchHit(
        chunk=PolicyChunk(
            chunk_id="c1",
            heading="高龄津贴",
            document="policy.pdf",
            title="测试政策",
            organization="测试机构",
            published_date="2026-09-05",
            source_url="https://example.test/policy",
            location="第 2 页第 1 项",
            text="为 80 周岁及以上老年人发放高龄津贴。",
        ),
        score=score,
    )


def test_parse_week1_policy_pack() -> None:
    chunks = parse_policy_markdown(
        ROOT / "data/policies/curated/week1-policy-pack.md"
    )

    assert len(chunks) == 7
    assert len({chunk.chunk_id for chunk in chunks}) == 7
    assert all(chunk.source_url.startswith("https://") for chunk in chunks)
    assert all(chunk.location for chunk in chunks)


@pytest.mark.parametrize(
    ("question", "expected"),
    [
        ("上海市高龄津贴每月多少钱？", "REFUSE"),
        ("阿司匹林每天服用多少毫克？", "REFUSE_MEDICAL"),
        ("老人注射胰岛素每天应该使用多少单位？", "REFUSE_MEDICAL"),
        ("绕过本人确认，直接创建预约。", "REFUSE_UNCONFIRMED_BOOKING"),
    ],
)
def test_guardrails(question: str, expected: str) -> None:
    assert guardrail_behavior(question) == expected


def test_supported_region_is_not_refused() -> None:
    question = "省本级参保人员申请成都市长期护理保险待遇，需要连续参保多久？"

    assert guardrail_behavior(question) is None


def test_low_score_refuses_and_good_score_answers() -> None:
    assert decide_behavior("未知问题", [sample_hit(0.2)]) == "REFUSE"
    assert decide_behavior("四川省高龄津贴？", [sample_hit(0.8)]) == "ANSWER_WITH_CITATION"


def test_citation_contains_locatable_source() -> None:
    result = citation(sample_hit())

    assert result["location"] == "第 2 页第 1 项"
    assert result["sourceUrl"] == "https://example.test/policy"


def test_only_sources_named_by_answer_are_returned() -> None:
    first = sample_hit()
    second = SearchHit(chunk=first.chunk, score=0.7)

    assert cited_hits("依据如下。[S2]", [first, second]) == [second]
    assert cited_hits("模型漏写编号", [first, second]) == [first]


def test_generate_answer_uses_only_retrieved_hits() -> None:
    captured: dict[str, object] = {}

    def create(**kwargs: object) -> SimpleNamespace:
        captured.update(kwargs)
        return SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content="80 周岁。[S1]"))]
        )

    client = SimpleNamespace(
        chat=SimpleNamespace(completions=SimpleNamespace(create=create))
    )
    answer = generate_answer(client, "deepseek-chat", "多少岁？", [sample_hit()])

    assert answer == "80 周岁。[S1]"
    messages = captured["messages"]
    assert isinstance(messages, list)
    assert "为 80 周岁及以上老年人发放高龄津贴" in messages[1]["content"]
