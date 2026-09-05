from pathlib import Path

import pytest

from care_agent_ai import ingest
from care_agent_ai.config import token_is_valid
from care_agent_ai.db import embedding_to_vector_string
from care_agent_ai.ingest import UploadRejected, chunk_markdown, chunk_pdf_text, validate_upload


ROOT = Path(__file__).resolve().parents[1]


def test_validate_markdown_ok() -> None:
    result = validate_upload("policy.md", "text/markdown", "# 标题\n\n正文。".encode())

    assert result.file_type == "markdown"
    assert len(result.sha256) == 64


def test_validate_pdf_ok() -> None:
    result = validate_upload("policy.pdf", "application/pdf", b"%PDF-1.4 content")

    assert result.file_type == "pdf"
    assert len(result.sha256) == 64


def test_validate_rejects_wrong_extension() -> None:
    with pytest.raises(UploadRejected) as exc:
        validate_upload("policy.txt", "text/plain", b"content")

    assert exc.value.code == "UNSUPPORTED_FILE_TYPE"


def test_validate_rejects_wrong_mime() -> None:
    with pytest.raises(UploadRejected) as exc:
        validate_upload("policy.pdf", "text/plain", b"%PDF-1.4")

    assert exc.value.code == "UNSUPPORTED_MIME"


def test_validate_rejects_oversize(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(ingest, "MAX_BYTES", 10)

    with pytest.raises(UploadRejected) as exc:
        validate_upload("policy.md", "text/markdown", b"x" * 11)

    assert exc.value.code == "FILE_TOO_LARGE"


def test_validate_rejects_empty_file() -> None:
    with pytest.raises(UploadRejected) as exc:
        validate_upload("policy.md", "text/markdown", b"")

    assert exc.value.code == "EMPTY_FILE"


def test_validate_rejects_bad_pdf_magic_bytes() -> None:
    with pytest.raises(UploadRejected) as exc:
        validate_upload("policy.pdf", "application/pdf", b"not a real pdf")

    assert exc.value.code == "INVALID_MAGIC_BYTES"


def test_validate_rejects_binary_markdown() -> None:
    with pytest.raises(UploadRejected) as exc:
        validate_upload("policy.md", "text/markdown", b"hello\x00world")

    assert exc.value.code == "INVALID_CONTENT"


def test_chunk_markdown_splits_headings() -> None:
    chunks = chunk_markdown("# 政策\n\n第一条 内容A。\n\n## 第二条\n\n内容B。")

    assert [chunk.section for chunk in chunks] == ["政策", "第二条"]
    assert chunks[0].content == "第一条 内容A。"
    assert all(chunk.page_number is None for chunk in chunks)


def test_chunk_markdown_without_headings_splits_paragraphs() -> None:
    chunks = chunk_markdown("第一段内容。\n\n第二段内容。")

    assert len(chunks) == 2
    assert chunks[0].content == "第一段内容。"


def test_chunk_text_pdf_preserves_page_numbers() -> None:
    payload = (ROOT / "data/policies/raw/02-basic-elderly-care-plan.pdf").read_bytes()

    chunks = chunk_pdf_text(payload)

    assert chunks
    assert chunks[0].page_number == 1
    assert all(chunk.page_number is not None for chunk in chunks)
    assert max(chunk.page_number for chunk in chunks) == 14


def test_chunk_scan_pdf_is_rejected() -> None:
    payload = (ROOT / "data/policies/raw/07-community-elderly-care-complex-guide-scan.pdf").read_bytes()

    with pytest.raises(UploadRejected) as exc:
        chunk_pdf_text(payload)

    assert exc.value.code == "SCAN_REQUIRES_MANUAL_MARKDOWN"


def test_embedding_to_vector_string_is_pgvector_compatible() -> None:
    assert embedding_to_vector_string([0.1, 0.2, 0.3]) == "[0.10000000,0.20000000,0.30000000]"


def test_token_is_valid_uses_constant_time(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("INTERNAL_TOKEN", "dev-secret")

    assert token_is_valid("dev-secret") is True
    assert token_is_valid("wrong") is False
    assert token_is_valid(None) is False

    monkeypatch.delenv("INTERNAL_TOKEN")
    assert token_is_valid("dev-secret") is False
