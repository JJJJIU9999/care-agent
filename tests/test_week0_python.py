from types import SimpleNamespace

from fastapi.testclient import TestClient

from care_agent_ai.app import app
from care_agent_ai.chat_probe import probe_chat
from care_agent_ai.embedding_probe import probe_embedding
from care_agent_ai.local_embedding_benchmark import percentile, summarize_ranks
from care_agent_ai.scan_check import is_scan


def test_health() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_embedding_probe_reports_dimensions() -> None:
    fake_model = SimpleNamespace(
        encode=lambda *_, **__: [[0.1, 0.2, 0.3]]
    )

    result = probe_embedding(fake_model, "fake-model", "测试")

    assert result["model"] == "fake-model"
    assert result["dimensions"] == 3
    assert result["durationMs"] >= 0


def test_chat_probe_reports_non_empty_response() -> None:
    fake_client = SimpleNamespace(
        chat=SimpleNamespace(
            completions=SimpleNamespace(
                create=lambda **_: SimpleNamespace(
                    choices=[
                        SimpleNamespace(message=SimpleNamespace(content="成都"))
                    ]
                )
            )
        )
    )

    result = probe_chat(fake_client, "fake-model", "测试")

    assert result["model"] == "fake-model"
    assert result["responseChars"] == 2
    assert result["durationMs"] >= 0


def test_embedding_metrics() -> None:
    assert summarize_ranks([1, 2, 6]) == {
        "hitAt1": 0.3333,
        "hitAt5": 0.6667,
        "mrr": 0.5556,
    }
    assert percentile([1, 2, 3, 4, 5], 0.95) == 5


def test_is_scan_requires_images_and_no_text() -> None:
    assert is_scan(text_chars=0, image_count=10) is True
    assert is_scan(text_chars=5, image_count=1) is True
    assert is_scan(text_chars=20, image_count=1) is False
    assert is_scan(text_chars=0, image_count=0) is False
    assert is_scan(text_chars=800, image_count=1) is False
