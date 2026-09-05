"""CareAgent 周 2：文档导入的校验、切片与解析（纯函数，不依赖数据库或模型）。

校验顺序与 docs/05-security.md 一致：扩展名白名单 → 声明 MIME → 大小 →
magic bytes → 解析后的正文非空。纯扫描件不调用 OCR，改提示转人工 Markdown。
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass
from io import BytesIO
from pathlib import Path

from pypdf import PdfReader

from care_agent_ai.scan_check import SCAN_TEXT_CHAR_THRESHOLD, is_scan

MAX_BYTES = 10 * 1024 * 1024  # 10 MB
ALLOWED_EXTENSIONS = {".md": "markdown", ".pdf": "pdf"}
ALLOWED_MIME = {
    ".md": {"text/markdown", "text/plain", "text/x-markdown"},
    ".pdf": {"application/pdf", "application/x-pdf"},
}
MAX_CHUNK_CHARS = 2000


class UploadRejected(ValueError):
    """带稳定错误码的上传拒绝；message 可安全返回给调用方。"""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


@dataclass(frozen=True)
class ValidatedUpload:
    file_type: str  # "markdown" | "pdf"
    sha256: str


@dataclass(frozen=True)
class Chunk:
    section: str
    page_number: int | None
    content: str


def validate_upload(filename: str, content_type: str, payload: bytes) -> ValidatedUpload:
    if not filename:
        raise UploadRejected("MISSING_FILENAME", "缺少文件名。")

    extension = Path(filename).suffix.lower()
    if extension not in ALLOWED_EXTENSIONS:
        raise UploadRejected(
            "UNSUPPORTED_FILE_TYPE", "仅支持 .md 与 .pdf 文件。"
        )

    declared = (content_type or "").split(";")[0].strip().lower()
    if declared not in ALLOWED_MIME[extension]:
        raise UploadRejected("UNSUPPORTED_MIME", "声明的 MIME 类型不受支持。")

    if not payload:
        raise UploadRejected("EMPTY_FILE", "文件内容为空。")

    if len(payload) > MAX_BYTES:
        raise UploadRejected("FILE_TOO_LARGE", "文件超过 10 MB 上限。")

    if extension == ".pdf":
        if not payload.startswith(b"%PDF-"):
            raise UploadRejected("INVALID_MAGIC_BYTES", "PDF 缺少 %PDF- 魔数。")
    else:
        if b"\x00" in payload:
            raise UploadRejected("INVALID_CONTENT", "Markdown 不能包含二进制内容。")
        try:
            text = payload.decode("utf-8")
        except UnicodeDecodeError as exc:
            raise UploadRejected("INVALID_CONTENT", "Markdown 必须是 UTF-8 文本。") from exc
        if not text.strip():
            raise UploadRejected("EMPTY_CONTENT", "解析后的正文为空。")

    return ValidatedUpload(
        file_type=ALLOWED_EXTENSIONS[extension],
        sha256=hashlib.sha256(payload).hexdigest(),
    )


def _split_long(content: str, max_chars: int) -> list[str]:
    content = content.strip()
    if len(content) <= max_chars:
        return [content] if content else []

    pieces: list[str] = []
    remaining = content
    while len(remaining) > max_chars:
        cut = remaining.rfind("\n", 0, max_chars)
        if cut < max_chars // 2:
            cut = remaining.rfind("。", 0, max_chars)
        if cut < max_chars // 2:
            cut = remaining.rfind(" ", 0, max_chars)
        if cut < max_chars // 2:
            cut = max_chars
        pieces.append(remaining[:cut].strip())
        remaining = remaining[cut:].strip()
    if remaining:
        pieces.append(remaining)
    return pieces


def chunk_markdown(text: str) -> list[Chunk]:
    parts = re.split(r"(?m)^(#{1,3}\s+.+)$", text)
    chunks: list[Chunk] = []
    if len(parts) == 1:
        # 无标题：按空行分段。
        chunks = [
            Chunk(section="", page_number=None, content=paragraph.strip())
            for paragraph in re.split(r"\n\s*\n", text)
            if paragraph.strip()
        ]
    else:
        if parts[0].strip():
            chunks.append(Chunk(section="", page_number=None, content=parts[0].strip()))
        for index in range(1, len(parts), 2):
            heading = re.sub(r"^#{1,3}\s+", "", parts[index]).strip()
            body = parts[index + 1].strip() if index + 1 < len(parts) else ""
            if body:
                chunks.append(Chunk(section=heading, page_number=None, content=body))

    expanded: list[Chunk] = []
    for chunk in chunks:
        for piece in _split_long(chunk.content, MAX_CHUNK_CHARS):
            expanded.append(Chunk(chunk.section, chunk.page_number, piece))
    return expanded


def _pdf_image_count(reader: PdfReader) -> int:
    image_count = 0
    for page in reader.pages:
        resources = page.get("/Resources") or {}
        xobjects = resources.get("/XObject") or {}
        image_count += sum(
            1
            for value in xobjects.values()
            if str(value.get("/Subtype")) == "/Image"
        )
    return image_count


def chunk_pdf_text(payload: bytes) -> list[Chunk]:
    reader = PdfReader(BytesIO(payload))
    text = "".join((page.extract_text() or "") for page in reader.pages)
    text_chars = len(text.strip())
    image_count = _pdf_image_count(reader)

    if is_scan(text_chars, image_count):
        raise UploadRejected(
            "SCAN_REQUIRES_MANUAL_MARKDOWN",
            "纯扫描件不做 OCR，请改传人工核对的 Markdown 文本。",
        )
    if not text.strip():
        raise UploadRejected("EMPTY_CONTENT", "PDF 未提取到可用文本。")

    chunks: list[Chunk] = []
    for page_index, page in enumerate(reader.pages, start=1):
        page_text = (page.extract_text() or "").strip()
        if not page_text:
            continue
        for piece in _split_long(page_text, MAX_CHUNK_CHARS):
            chunks.append(Chunk(section="", page_number=page_index, content=piece))
    return chunks


def parse_document(file_type: str, payload: bytes) -> list[Chunk]:
    if file_type == "markdown":
        return chunk_markdown(payload.decode("utf-8"))
    if file_type == "pdf":
        return chunk_pdf_text(payload)
    raise UploadRejected("UNSUPPORTED_FILE_TYPE", "仅支持 .md 与 .pdf 文件。")
