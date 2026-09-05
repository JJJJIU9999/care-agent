from __future__ import annotations

import argparse
import hashlib
import json
import math
from datetime import UTC, datetime
from importlib.metadata import version
from pathlib import Path
from time import perf_counter
from typing import Any


MODEL_CONFIGS = {
    "bge": {
        "name": "BAAI/bge-small-zh-v1.5",
        "revision": "7999e1d3359715c523056ef9478215996d62a620",
        "queryPrefix": "为这个句子生成表示以用于检索相关文章：",
        "documentPrefix": "",
    },
    "e5": {
        "name": "intfloat/multilingual-e5-small",
        "revision": "614241f622f53c4eeff9890bdc4f31cfecc418b3",
        "queryPrefix": "query: ",
        "documentPrefix": "passage: ",
    },
}


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def summarize_ranks(ranks: list[int], top_k: int = 5) -> dict[str, float]:
    if not ranks:
        raise ValueError("At least one in-scope rank is required")

    return {
        "hitAt1": round(sum(rank <= 1 for rank in ranks) / len(ranks), 4),
        "hitAt5": round(sum(rank <= top_k for rank in ranks) / len(ranks), 4),
        "mrr": round(sum(1 / rank for rank in ranks) / len(ranks), 4),
    }


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    index = max(0, math.ceil(len(ordered) * fraction) - 1)
    return ordered[index]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_benchmark(model_key: str, root: Path) -> dict[str, Any]:
    from sentence_transformers import SentenceTransformer

    config = MODEL_CONFIGS[model_key]
    questions_path = root / "data/evaluation/week0_questions.jsonl"
    corpus_path = root / "data/evaluation/week0_embedding_corpus.jsonl"
    questions = read_jsonl(questions_path)
    corpus = read_jsonl(corpus_path)

    load_started = perf_counter()
    model = SentenceTransformer(
        config["name"],
        revision=config["revision"],
        device="cpu",
        cache_folder=str(root / ".model-cache"),
    )
    load_ms = (perf_counter() - load_started) * 1000

    document_texts = [config["documentPrefix"] + item["text"] for item in corpus]
    document_started = perf_counter()
    document_embeddings = model.encode(
        document_texts,
        normalize_embeddings=True,
        show_progress_bar=False,
    )
    document_ms = (perf_counter() - document_started) * 1000

    ranks: list[int] = []
    query_durations: list[float] = []
    details: list[dict[str, Any]] = []

    for question in questions:
        query_started = perf_counter()
        query_embedding = model.encode(
            [config["queryPrefix"] + question["question"]],
            normalize_embeddings=True,
            show_progress_bar=False,
        )
        query_durations.append((perf_counter() - query_started) * 1000)
        scores = (query_embedding @ document_embeddings.T)[0]
        ordered_indexes = sorted(
            range(len(corpus)), key=lambda index: float(scores[index]), reverse=True
        )
        top_ids = [corpus[index]["id"] for index in ordered_indexes[:5]]
        detail: dict[str, Any] = {
            "questionId": question["id"],
            "top5PassageIds": top_ids,
            "topScore": round(float(scores[ordered_indexes[0]]), 6),
        }

        if question["scope"] == "IN_SCOPE":
            gold_ids = {
                item["id"]
                for item in corpus
                if question["id"] in item["goldQuestionIds"]
            }
            if not gold_ids:
                raise ValueError(f"Missing gold passage for {question['id']}")
            rank = next(
                index
                for index, corpus_index in enumerate(ordered_indexes, start=1)
                if corpus[corpus_index]["id"] in gold_ids
            )
            ranks.append(rank)
            detail["goldRank"] = rank

        details.append(detail)

    metrics = summarize_ranks(ranks)
    return {
        "capturedAt": datetime.now(UTC).isoformat(),
        "model": config["name"],
        "modelRevision": config["revision"],
        "device": "cpu",
        "dimensions": int(document_embeddings.shape[1]),
        "corpusCount": len(corpus),
        "queryCount": len(questions),
        "inScopeQueryCount": len(ranks),
        "parameters": {
            "normalizeEmbeddings": True,
            "topK": 5,
            "queryPrefix": config["queryPrefix"],
            "documentPrefix": config["documentPrefix"],
        },
        "datasetSha256": {
            "questions": sha256(questions_path),
            "corpus": sha256(corpus_path),
        },
        "dependencyVersions": {
            "sentenceTransformers": version("sentence-transformers"),
            "torch": version("torch"),
            "transformers": version("transformers"),
        },
        "loadMs": round(load_ms, 2),
        "documentBatchMs": round(document_ms, 2),
        "averageQueryMs": round(sum(query_durations) / len(query_durations), 2),
        "p95QueryMs": round(percentile(query_durations, 0.95), 2),
        "errorCount": 0,
        "apiCostUsd": 0,
        **metrics,
        "details": details,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", choices=MODEL_CONFIGS, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    result = run_benchmark(args.model, root)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n")
    print(rendered)


if __name__ == "__main__":
    main()
