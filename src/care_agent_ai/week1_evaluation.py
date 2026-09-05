from __future__ import annotations

import argparse
import json
import re
from datetime import UTC, datetime
from pathlib import Path
from time import perf_counter
from typing import Any

from care_agent_ai.local_embedding_benchmark import percentile, read_jsonl, summarize_ranks
from care_agent_ai.rag_cli import (
    DEFAULT_KNOWLEDGE_PATH,
    decide_behavior,
    embed_chunks,
    guardrail_behavior,
    load_embedding_model,
    parse_policy_markdown,
    retrieve,
)


ROOT = Path(__file__).resolve().parents[2]


def normalized(text: str) -> str:
    return re.sub(r"\s+", "", text)


def evaluate(
    root: Path = ROOT, questions_path: Path | None = None
) -> dict[str, Any]:
    questions_path = questions_path or (root / "data/evaluation/week0_questions.jsonl")
    questions = read_jsonl(questions_path)
    chunks = parse_policy_markdown(DEFAULT_KNOWLEDGE_PATH)
    model = load_embedding_model()

    index_started = perf_counter()
    embeddings = embed_chunks(model, chunks)
    index_ms = (perf_counter() - index_started) * 1000

    ranks: list[int] = []
    durations: list[float] = []
    correct_count = 0
    details: list[dict[str, Any]] = []

    for question in questions:
        started = perf_counter()
        guarded = guardrail_behavior(question["question"])
        hits = [] if guarded else retrieve(question["question"], chunks, embeddings, model)
        behavior = guarded or decide_behavior(question["question"], hits)
        durations.append((perf_counter() - started) * 1000)
        detail: dict[str, Any] = {
            "questionId": question["id"],
            "expectedBehavior": question["expectedBehavior"],
            "actualBehavior": behavior,
            "top5ChunkIds": [hit.chunk.chunk_id for hit in hits],
            "topScore": round(hits[0].score, 6) if hits else None,
        }

        correct = behavior == question["expectedBehavior"]
        if question["scope"] == "IN_SCOPE":
            expected = normalized(question["expectedSnippet"])
            gold_indexes = [
                index
                for index, hit in enumerate(hits, start=1)
                if hit.chunk.document == question["expectedDocument"]
                and expected in normalized(hit.chunk.text)
            ]
            rank = gold_indexes[0] if gold_indexes else len(chunks) + 1
            ranks.append(rank)
            detail["goldRank"] = rank
            correct = correct and rank <= 5

        detail["correct"] = correct
        if not correct:
            detail["failureReason"] = (
                f"expected {question['expectedBehavior']}, got {behavior}"
            )
        else:
            correct_count += 1
        details.append(detail)

    try:
        questions_file = str(questions_path.resolve().relative_to(root))
    except ValueError:
        questions_file = str(questions_path)

    metrics = summarize_ranks(ranks)
    return {
        "capturedAt": datetime.now(UTC).isoformat(),
        "knowledgeFile": str(DEFAULT_KNOWLEDGE_PATH.relative_to(root)),
        "questionsFile": questions_file,
        "chunkCount": len(chunks),
        "questionCount": len(questions),
        "behaviorCorrect": correct_count,
        "behaviorAccuracy": round(correct_count / len(questions), 4),
        "indexMs": round(index_ms, 2),
        "averageQuestionMs": round(sum(durations) / len(durations), 2),
        "p95QuestionMs": round(percentile(durations, 0.95), 2),
        **metrics,
        "failureReasons": [
            item["failureReason"] for item in details if "failureReason" in item
        ],
        "details": details,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="CareAgent 检索与拒答评测")
    parser.add_argument("--questions", type=Path, default=None)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = evaluate(questions_path=args.questions)
    rendered = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
