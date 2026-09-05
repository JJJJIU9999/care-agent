"""CareAgent 周 2：FastAPI 内部接口。

- 沿用周 0 的 ``GET /health``。
- 内部上传/状态查询：``/internal/v1/knowledge/documents``。
- 周 1 CLI 能力的同步封装：``/internal/v1/rag/answer``。
- 内部接口统一使用 ``X-Internal-Token``（常量时间比较）。
"""

from __future__ import annotations

import os
from datetime import date
from typing import Annotated, Any

import psycopg
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from care_agent_ai import rag_service
from care_agent_ai.config import rag_database_url, token_is_valid
from care_agent_ai.ingest import UploadRejected, validate_upload

app = FastAPI(title="CareAgent AI")


class AnswerRequest(BaseModel):
    question: str


_UPLOAD_STATUS_CODES = {
    "FILE_TOO_LARGE": 413,
    "UNSUPPORTED_FILE_TYPE": 415,
    "UNSUPPORTED_MIME": 415,
}


@app.exception_handler(HTTPException)
async def _http_exception_handler(_, exc: HTTPException) -> JSONResponse:
    detail = exc.detail
    if isinstance(detail, dict):
        code = detail.get("code", "ERROR")
        message = detail.get("message", "请求处理失败。")
    else:
        code, message = "ERROR", str(detail)
    return JSONResponse(status_code=exc.status_code, content={"code": code, "message": message})


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def require_internal_token(
    x_internal_token: Annotated[str | None, Header()] = None,
) -> None:
    if not token_is_valid(x_internal_token):
        raise HTTPException(
            status_code=401,
            detail={"code": "INVALID_INTERNAL_TOKEN", "message": "内部认证失败。"},
        )


@app.post("/internal/v1/knowledge/documents", status_code=202)
async def upload_knowledge_document(
    file: Annotated[UploadFile, File(...)],
    title: Annotated[str, Form(...)],
    issuingOrganization: Annotated[str, Form(...)],
    sourceUrl: Annotated[str, Form()] = "",
    effectiveDate: Annotated[str, Form()] = "",
    _: None = Depends(require_internal_token),
) -> dict[str, Any]:
    if not title.strip() or not issuingOrganization.strip():
        raise HTTPException(
            status_code=422,
            detail={"code": "MISSING_METADATA", "message": "title 与 issuingOrganization 必填。"},
        )

    effective = None
    if effectiveDate.strip():
        try:
            effective = date.fromisoformat(effectiveDate.strip())
        except ValueError as exc:
            raise HTTPException(
                status_code=422,
                detail={"code": "INVALID_EFFECTIVE_DATE", "message": "effectiveDate 必须为 YYYY-MM-DD。"},
            ) from exc

    payload = await file.read()
    try:
        validated = validate_upload(file.filename or "", file.content_type or "", payload)
    except UploadRejected as exc:
        status = _UPLOAD_STATUS_CODES.get(exc.code, 422)
        raise HTTPException(status_code=status, detail={"code": exc.code, "message": exc.message}) from exc

    try:
        result = rag_service.ingest_document(
            rag_database_url(),
            validated,
            {
                "title": title.strip(),
                "issuingOrganization": issuingOrganization.strip(),
                "sourceUrl": sourceUrl.strip(),
                "effectiveDate": effective,
                "payload": payload,
            },
        )
    except psycopg.OperationalError as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "KNOWLEDGE_STORE_UNAVAILABLE", "message": "知识库暂不可用，请稍后重试。"},
        ) from exc

    response: dict[str, Any] = {
        "documentId": result["documentId"],
        "status": result["status"],
    }
    if result.get("jobId"):
        response["jobId"] = result["jobId"]
    if result.get("failureReason"):
        response["failureReason"] = result["failureReason"]
    return response


@app.get("/internal/v1/knowledge/documents/{documentId}")
def get_knowledge_document_status(
    documentId: str,
    _: None = Depends(require_internal_token),
) -> dict[str, Any]:
    try:
        result = rag_service.document_status(rag_database_url(), documentId)
    except psycopg.OperationalError as exc:
        raise HTTPException(
            status_code=503,
            detail={"code": "KNOWLEDGE_STORE_UNAVAILABLE", "message": "知识库暂不可用，请稍后重试。"},
        ) from exc
    if result is None:
        raise HTTPException(
            status_code=404,
            detail={"code": "DOCUMENT_NOT_FOUND", "message": "文档不存在。"},
        )
    return result


@app.post("/internal/v1/rag/answer")
def answer(body: AnswerRequest, _: None = Depends(require_internal_token)) -> dict[str, Any]:
    question = body.question.strip()
    if not 1 <= len(question) <= 1000:
        raise HTTPException(
            status_code=422,
            detail={"code": "INVALID_QUESTION", "message": "问题长度必须为 1–1000 个字符。"},
        )

    api_key = os.environ.get("OPENAI_API_KEY")
    chat_model = os.environ.get("CHAT_MODEL")
    if not api_key or not chat_model:
        raise HTTPException(
            status_code=503,
            detail={"code": "MODEL_UNAVAILABLE", "message": "智能问答暂时不可用，请稍后重试。"},
        )

    from openai import OpenAI

    client = OpenAI(api_key=api_key, base_url=os.environ.get("OPENAI_BASE_URL"))
    return rag_service.answer_question(question, client, chat_model)
