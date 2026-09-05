"""Week 0 scan detection: distinguish an image-only (scanned) PDF from a text PDF.

A document counts as a scanned sample only when it has no meaningful text layer
(pypdf extracts no content) but embeds raster images. Text-layer PDFs — even
born-digital ones with an embedded thumbnail — are not scans, so we never
mislabel an OCR-able or already-digital document as a scan. OCR stays out of
scope: scans are transcribed by hand later.
"""

from __future__ import annotations

from pathlib import Path

from pypdf import PdfReader

# Below this many non-whitespace characters we treat the text layer as absent.
SCAN_TEXT_CHAR_THRESHOLD = 20


def is_scan(text_chars: int, image_count: int) -> bool:
    """Return True only for an image-only document with no usable text layer."""
    return image_count > 0 and text_chars < SCAN_TEXT_CHAR_THRESHOLD


def inspect_pdf(path: str | Path) -> dict:
    """Open a PDF and report pages, text length, embedded image count, and scan flag."""
    reader = PdfReader(str(path))
    text = "".join((page.extract_text() or "") for page in reader.pages)
    image_count = 0
    for page in reader.pages:
        resources = page.get("/Resources") or {}
        xobjects = resources.get("/XObject") or {}
        image_count += sum(
            1
            for value in xobjects.values()
            if str(value.get("/Subtype")) == "/Image"
        )
    text_chars = len(text.strip())
    return {
        "pages": len(reader.pages),
        "textChars": text_chars,
        "images": image_count,
        "isScan": is_scan(text_chars, image_count),
    }
