-- CareAgent 周 2：Python 服务维护的 rag schema 初始化。
-- 仅在 PostgreSQL 数据卷首次创建时运行一次（docker-entrypoint-initdb.d）。
-- 向量维度固定为 512，与周 0 选定的 BAAI/bge-small-zh-v1.5 一致。
-- Alembic 迁移按路线图延后；当前使用本初始化脚本。

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS rag;

-- 知识文档元数据：标题、机构、生效日期、来源 URL、SHA-256、文件类型、处理方式、状态与失败原因。
CREATE TABLE IF NOT EXISTS rag.knowledge_document (
    id                    UUID        PRIMARY KEY,
    title                 TEXT        NOT NULL,
    issuing_organization  TEXT        NOT NULL,
    effective_date        DATE,
    source_url            TEXT,
    sha256                TEXT        NOT NULL UNIQUE,
    file_type             TEXT        NOT NULL,
    parse_decision        TEXT        NOT NULL,
    status                TEXT        NOT NULL,
    failure_reason        TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 文档切片：章节、页码、序号、正文与 512 维 Embedding。
CREATE TABLE IF NOT EXISTS rag.document_chunk (
    id          UUID        PRIMARY KEY,
    document_id UUID        NOT NULL REFERENCES rag.knowledge_document(id) ON DELETE CASCADE,
    section     TEXT        NOT NULL,
    page_number INTEGER,
    chunk_index INTEGER     NOT NULL,
    content     TEXT        NOT NULL,
    embedding   vector(512) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS document_chunk_embedding_hnsw
    ON rag.document_chunk USING hnsw (embedding vector_cosine_ops);

-- 导入任务：PENDING → PROCESSING → COMPLETED | FAILED，失败记录可理解原因。
CREATE TABLE IF NOT EXISTS rag.ingestion_job (
    id             UUID        PRIMARY KEY,
    document_id    UUID        NOT NULL REFERENCES rag.knowledge_document(id) ON DELETE CASCADE,
    status         TEXT        NOT NULL,
    failure_reason TEXT,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
