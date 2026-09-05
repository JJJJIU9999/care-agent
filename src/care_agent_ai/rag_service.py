"""CareAgent 周 2：把周 1 CLI 能力封装为服务，并提供导入编排。

- ``answer_question`` 复用 rag_cli 的在内存检索 + 守卫 + 带引用回答。
- ``ingest_document`` 完成「校验通过后的上传 → 切片 → Embedding → 写入 pgvector」。
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any, Sequence
from uuid import uuid4

from care_agent_ai import db
from care_agent_ai.ingest import Chunk, ValidatedUpload, parse_document
from care_agent_ai.local_embedding_benchmark import MODEL_CONFIGS
from care_agent_ai.rag_cli import (
    DEFAULT_KNOWLEDGE_PATH,
    embed_chunks,
    load_embedding_model,
    parse_policy_markdown,
    run_question,
)

_embedding_model: Any | None = None
_curated_index: tuple[Any, Any, Any] | None = None


def get_embedding_model() -> Any:
    global _embedding_model
    if _embedding_model is None:
        _embedding_model = load_embedding_model()
    return _embedding_model


def embed_texts(texts: Sequence[str]) -> Any:
    model = get_embedding_model()
    prefix = MODEL_CONFIGS["bge"]["documentPrefix"]
    return model.encode(
        [prefix + text for text in texts],
        normalize_embeddings=True,
        show_progress_bar=False,
    )


def get_curated_index() -> tuple[Any, Any, Any]:
    """惰性加载周 1 人工知识包及其 BGE Embedding，避免拖慢 /health。"""
    global _curated_index
    if _curated_index is None:
        chunks = parse_policy_markdown(DEFAULT_KNOWLEDGE_PATH)
        model = get_embedding_model()
        embeddings = embed_chunks(model, chunks)
        _curated_index = (chunks, model, embeddings)
    return _curated_index


def answer_question(
    question: str, chat_client: Any, chat_model: str
) -> dict[str, Any]:
    chunks, model, embeddings = get_curated_index()
    return run_question(
        question, chunks, embeddings, model, chat_client, chat_model
    )


def ingest_document(
    dsn: str, validated: ValidatedUpload, metadata: dict[str, Any]
) -> dict[str, Any]:
    """把通过校验的文件切片、Embedding 并写入 rag schema。

    状态机 PENDING → PROCESSING → COMPLETED | FAILED 在同一连接内完成；
    SHA-256 重复的文件直接返回既有文档，不重复入库。
    """
    conn = db.connect(dsn)
    try:
        existing = db.find_document_by_sha256(conn, validated.sha256)
        if existing:
            conn.close()
            return {
                "documentId": existing["id"],
                "status": existing["status"],
                "failureReason": existing["failure_reason"],
                "duplicate": True,
            }

        document_id = str(uuid4())
        job_id = str(uuid4())
        now = datetime.now(UTC)
        document = {
            "id": document_id,
            "title": metadata["title"],
            "issuingOrganization": metadata["issuingOrganization"],
            "effectiveDate": metadata["effectiveDate"],
            "sourceUrl": metadata["sourceUrl"],
            "sha256": validated.sha256,
            "fileType": validated.file_type,
            "parseDecision": (
                "MANUAL_MARKDOWN" if validated.file_type == "markdown" else "AUTO_TEXT"
            ),
            "status": "PENDING",
        }
        db.insert_document(conn, document)
        db.insert_job(
            conn,
            {
                "id": job_id,
                "documentId": document_id,
                "status": "PENDING",
                "startedAt": now,
            },
        )
        conn.commit()

        # 解析/Embedding 失败只标记任务 FAILED，不丢弃文档元数据。
        try:
            db.update_document_status(conn, document_id, "PROCESSING")
            db.update_job_status(conn, job_id, "PROCESSING")
            conn.commit()

            chunks: list[Chunk] = parse_document(validated.file_type, metadata["payload"])
            embeddings = embed_texts([chunk.content for chunk in chunks])
            for index, chunk in enumerate(chunks):
                db.insert_chunk(
                    conn,
                    {
                        "id": str(uuid4()),
                        "documentId": document_id,
                        "section": chunk.section,
                        "pageNumber": chunk.page_number,
                        "chunkIndex": index,
                        "content": chunk.content,
                    },
                    embeddings[index].tolist(),
                )
            db.update_document_status(conn, document_id, "COMPLETED")
            db.update_job_status(conn, job_id, "COMPLETED", finished_at=datetime.now(UTC))
            conn.commit()
            return {
                "documentId": document_id,
                "jobId": job_id,
                "status": "COMPLETED",
                "chunkCount": len(chunks),
            }
        except Exception as exc:  # noqa: BLE001 - 记录安全化原因
            reason = _safe_failure_reason(exc)
            conn.rollback()
            db.update_document_status(conn, document_id, "FAILED", reason)
            db.update_job_status(
                conn, job_id, "FAILED", reason, finished_at=datetime.now(UTC)
            )
            conn.commit()
            return {
                "documentId": document_id,
                "jobId": job_id,
                "status": "FAILED",
                "failureReason": reason,
            }
    finally:
        conn.close()


def document_status(dsn: str, document_id: str) -> dict[str, Any] | None:
    conn = db.connect(dsn)
    try:
        document = db.get_document(conn, document_id)
        if not document:
            return None
        return {
            "documentId": document["id"],
            "title": document["title"],
            "status": document["status"],
            "failureReason": document["failure_reason"],
        }
    finally:
        conn.close()


def _safe_failure_reason(exc: Exception) -> str:
    """返回可安全展示的失败原因，不泄漏堆栈、SQL 或密钥。"""
    from care_agent_ai.ingest import UploadRejected

    if isinstance(exc, UploadRejected):
        return exc.message
    return "文档解析或入库失败，请检查文件格式后重试。"
