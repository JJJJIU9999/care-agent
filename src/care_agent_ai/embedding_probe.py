import os
from pathlib import Path
from time import perf_counter
from typing import Any


DEFAULT_MODEL = "BAAI/bge-small-zh-v1.5"
MODEL_REVISION = "7999e1d3359715c523056ef9478215996d62a620"


def probe_embedding(
    embedding_model: Any, model_name: str, text: str
) -> dict[str, int | float | str]:
    started_at = perf_counter()
    vector = embedding_model.encode([text], normalize_embeddings=True)[0]
    if len(vector) == 0:
        raise ValueError("Embedding model returned an empty vector")

    return {
        "model": model_name,
        "dimensions": len(vector),
        "durationMs": round((perf_counter() - started_at) * 1000, 2),
    }


def main() -> None:
    from sentence_transformers import SentenceTransformer

    model_name = os.environ.get("EMBEDDING_MODEL") or DEFAULT_MODEL
    root = Path(__file__).resolve().parents[2]
    model = SentenceTransformer(
        model_name,
        revision=MODEL_REVISION if model_name == DEFAULT_MODEL else None,
        device="cpu",
        cache_folder=str(root / ".model-cache"),
    )
    result = probe_embedding(model, model_name, "成都市高龄津贴如何申请？")
    print(result)


if __name__ == "__main__":
    main()
