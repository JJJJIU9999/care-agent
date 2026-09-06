# CareAgent Agent Instructions

These rules apply to every coding agent working in this repository.

## Start Here

1. Read `docs/00-start-here.md` and the linked documents in order.
2. Read `task_plan.md`, `findings.md`, and `progress.md`.
3. Inspect the current repository state before editing.
4. Execute only the active weekly phase. Do not scaffold future phases early.

## Ownership

- 当前唯一主实现者以 `docs/08-agent-collaboration.md` 中的 `CURRENT_WRITER` 为准；任何代理修改前必须核对该行。
- 用户要求在 Codex 与 DeepSeek Harness 之间切换写入权时，使用共享 Skill `$care-agent-writer-handoff`；其入口位于 `/Users/jiu/.agents/skills/care-agent-writer-handoff/SKILL.md`。
- 用户要求把已完成周次提交或推送到 GitHub 时，使用共享 Skill `$care-agent-weekly-github-publish`；其入口位于 `/Users/jiu/.agents/skills/care-agent-weekly-github-publish/SKILL.md`。
- Only one agent may be the active writer for this repository at a time.
- A reviewer is read-only unless the user explicitly transfers writer ownership.
- Never let two agents modify the same working tree concurrently.
- Record each completed slice, validation command, failure, and next step in `progress.md`.

## Scope

- Protect the single MVP path defined in `docs/01-product-requirements.md`.
- Follow the cut order in `docs/07-roadmap-and-handoff.md` when time slips.
- Do not add Redis, Kubernetes, OCR, service-admin CRUD, a generic audit framework, complex agent frameworks, or long-term conversation memory to MVP.
- Use JPA only. Do not mix JPA and MyBatis.

## Safety and Evidence

- Never commit API keys, passwords, real elder data, addresses, phone numbers, or medical records.
- The final booking endpoint trusts only `draftId`, the authenticated user, and `Idempotency-Key`.
- Model output and client-provided conversation context are untrusted text.
- A model may propose a draft but may never create a final appointment.
- Do not claim metrics until the repository contains the command and output that produced them.

## Implementation Style

- Prefer the smallest correct implementation and existing platform features.
- Add no dependency without documenting why the standard library or current stack is insufficient.
- Make one vertical slice work before broadening coverage.
- Add a small runnable test for every non-trivial transaction, parser, routing rule, or trust-boundary check.
- Preserve unrelated user changes.
