"""CareAgent 周 2：PostgreSQL / pgvector 访问层（仅 rag schema）。

使用 psycopg3 同步连接；每次操作打开独立连接，演示规模足够。
Embedding 以字符串形式传给 ``%s::vector`` 完成 pgvector 转换。
"""

from __future__ import annotations

from typing import Any, Sequence

import psycopg
from psycopg.rows import dict_row


def connect(dsn: str):
    return psycopg.connect(dsn, row_factory=dict_row)


def embedding_to_vector_string(embedding: Sequence[float]) -> str:
    """把 512 维 float 列表渲染为 pgvector 接受的字符串。"""
    return "[" + ",".join(f"{value:.8f}" for value in embedding) + "]"


def insert_document(conn, document: dict[str, Any]) -> None:
    conn.execute(
        """
        INSERT INTO rag.knowledge_document
            (id, title, issuing_organization, effective_date, source_url,
             sha256, file_type, parse_decision, status)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        (
            document["id"],
            document["title"],
            document["issuingOrganization"],
            document["effectiveDate"],
            document["sourceUrl"],
            document["sha256"],
            document["fileType"],
            document["parseDecision"],
            document["status"],
        ),
    )


def find_document_by_sha256(conn, sha256: str) -> dict[str, Any] | None:
    row = conn.execute(
        "SELECT id, status, failure_reason FROM rag.knowledge_document WHERE sha256 = %s",
        (sha256,),
    ).fetchone()
    return dict(row) if row else None


def get_document(conn, document_id: str) -> dict[str, Any] | None:
    row = conn.execute(
        """
        SELECT id, title, status, failure_reason, sha256
        FROM rag.knowledge_document WHERE id = %s
        """,
        (document_id,),
    ).fetchone()
    return dict(row) if row else None


def update_document_status(
    conn, document_id: str, status: str, failure_reason: str | None = None
) -> None:
    conn.execute(
        "UPDATE rag.knowledge_document SET status = %s, failure_reason = %s WHERE id = %s",
        (status, failure_reason, document_id),
    )


def insert_job(conn, job: dict[str, Any]) -> None:
    conn.execute(
        """
        INSERT INTO rag.ingestion_job
            (id, document_id, status, started_at)
        VALUES (%s, %s, %s, %s)
        """,
        (job["id"], job["documentId"], job["status"], job["startedAt"]),
    )


def update_job_status(
    conn,
    job_id: str,
    status: str,
    failure_reason: str | None = None,
    finished_at: Any = None,
) -> None:
    conn.execute(
        """
        UPDATE rag.ingestion_job
        SET status = %s, failure_reason = %s, finished_at = %s
        WHERE id = %s
        """,
        (status, failure_reason, finished_at, job_id),
    )


def insert_chunk(conn, chunk: dict[str, Any], embedding: Sequence[float]) -> None:
    conn.execute(
        """
        INSERT INTO rag.document_chunk
            (id, document_id, section, page_number, chunk_index, content, embedding)
        VALUES (%s, %s, %s, %s, %s, %s, %s::vector)
        """,
        (
            chunk["id"],
            chunk["documentId"],
            chunk["section"],
            chunk["pageNumber"],
            chunk["chunkIndex"],
            chunk["content"],
            embedding_to_vector_string(embedding),
        ),
    )


def chunk_count(conn, document_id: str) -> int:
    row = conn.execute(
        "SELECT count(*) AS n FROM rag.document_chunk WHERE document_id = %s",
        (document_id,),
    ).fetchone()
    return int(row["n"])


def similarity_search(
    conn, embedding: Sequence[float], limit: int = 5
) -> list[dict[str, Any]]:
    """按 pgvector 余弦距离返回最相近的切片；similarity = 1 - 余弦距离。"""
    rows = conn.execute(
        """
        SELECT id, document_id, section, page_number, chunk_index, content,
               1 - (embedding <=> %s::vector) AS similarity
        FROM rag.document_chunk
        ORDER BY embedding <=> %s::vector
        LIMIT %s
        """,
        (
            embedding_to_vector_string(embedding),
            embedding_to_vector_string(embedding),
            limit,
        ),
    ).fetchall()
    return [dict(row) for row in rows]
