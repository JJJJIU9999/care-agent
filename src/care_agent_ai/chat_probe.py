import os
from time import perf_counter
from typing import Any


def probe_chat(client: Any, model: str, prompt: str) -> dict[str, int | float | str]:
    started_at = perf_counter()
    response = client.chat.completions.create(
        model=model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0,
    )
    content = response.choices[0].message.content or ""
    if not content.strip():
        raise ValueError("Chat provider returned empty content")

    return {
        "model": model,
        "responseChars": len(content),
        "durationMs": round((perf_counter() - started_at) * 1000, 2),
    }


def main() -> None:
    from openai import OpenAI

    model = os.environ["CHAT_MODEL"]
    result = probe_chat(OpenAI(), model, "请只回答：成都。")
    print(result)


if __name__ == "__main__":
    main()

