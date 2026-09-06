FROM ghcr.io/astral-sh/uv:0.12.7 AS uv
FROM python:3.14-slim

COPY --from=uv /uv /uvx /bin/
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
RUN uv sync --frozen --no-dev

ENV PATH="/app/.venv/bin:$PATH"
EXPOSE 8010
CMD ["uvicorn", "care_agent_ai.app:app", "--host", "0.0.0.0", "--port", "8010"]
