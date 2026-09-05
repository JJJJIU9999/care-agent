import pytest
from fastapi.testclient import TestClient

from care_agent_ai.app import app


def _client(monkeypatch: pytest.MonkeyPatch, token: str = "dev-secret") -> TestClient:
    monkeypatch.setenv("INTERNAL_TOKEN", token)
    return TestClient(app)


def test_internal_endpoints_require_token(monkeypatch: pytest.MonkeyPatch) -> None:
    client = _client(monkeypatch)

    assert client.get("/internal/v1/knowledge/documents/some-id").status_code == 401
    assert (
        client.get(
            "/internal/v1/knowledge/documents/some-id",
            headers={"X-Internal-Token": "wrong"},
        ).status_code
        == 401
    )
    assert client.post("/internal/v1/rag/answer", json={"question": "x"}).status_code == 401


def test_upload_rejects_wrong_extension(monkeypatch: pytest.MonkeyPatch) -> None:
    client = _client(monkeypatch)

    response = client.post(
        "/internal/v1/knowledge/documents",
        headers={"X-Internal-Token": "dev-secret"},
        files={"file": ("evil.txt", b"hello", "text/plain")},
        data={"title": "标题", "issuingOrganization": "机构"},
    )

    assert response.status_code == 415
    assert response.json()["code"] == "UNSUPPORTED_FILE_TYPE"


def test_upload_rejects_fake_pdf(monkeypatch: pytest.MonkeyPatch) -> None:
    client = _client(monkeypatch)

    response = client.post(
        "/internal/v1/knowledge/documents",
        headers={"X-Internal-Token": "dev-secret"},
        files={"file": ("fake.pdf", b"not a pdf", "application/pdf")},
        data={"title": "标题", "issuingOrganization": "机构"},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "INVALID_MAGIC_BYTES"


def test_upload_rejects_missing_metadata(monkeypatch: pytest.MonkeyPatch) -> None:
    client = _client(monkeypatch)

    response = client.post(
        "/internal/v1/knowledge/documents",
        headers={"X-Internal-Token": "dev-secret"},
        files={"file": ("policy.md", "# 标题\n\n正文", "text/markdown")},
        data={"title": "   ", "issuingOrganization": "机构"},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "MISSING_METADATA"


def test_answer_rejects_empty_question(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("CHAT_MODEL", raising=False)
    client = _client(monkeypatch)

    response = client.post(
        "/internal/v1/rag/answer",
        headers={"X-Internal-Token": "dev-secret"},
        json={"question": ""},
    )

    assert response.status_code == 422
    assert response.json()["code"] == "INVALID_QUESTION"


def test_answer_reports_model_unavailable(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.delenv("CHAT_MODEL", raising=False)
    client = _client(monkeypatch)

    response = client.post(
        "/internal/v1/rag/answer",
        headers={"X-Internal-Token": "dev-secret"},
        json={"question": "四川省高龄津贴面向多少周岁以上老年人？"},
    )

    assert response.status_code == 503
    assert response.json()["code"] == "MODEL_UNAVAILABLE"
