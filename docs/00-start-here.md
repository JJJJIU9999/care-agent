# 开发入口

## 项目状态

截至 2026-09-06，CareAgent 的周 0–3 均已通过验收。周 3 已完成 Spring Security/JWT、JPA/Flyway、服务与时段、预约草案、幂等确认、原子容量扣减、取消回补和管理员知识代理；Java 测试 6/6、Python 回归 34/34，并通过真实 PostgreSQL 与 Compose 健康检查。周 3 已发布到 GitHub `week3` 分支。当前下一步是按路线图进入周 4：固定 SSE 事件协议，串联 Java 登录用户、Python RAG 与预约草案。

## 阅读顺序

1. `README.md`
2. `AGENTS.md`
3. `task_plan.md`、`findings.md`、`progress.md`
4. `docs/01-product-requirements.md`
5. `docs/02-architecture.md`
6. `docs/03-api-contract.md`
7. `docs/04-data-model.md`
8. `docs/05-security.md`
9. `docs/06-test-and-evaluation.md`
10. `docs/07-roadmap-and-handoff.md`
11. `docs/08-agent-collaboration.md`
12. `docs/09-week0-feasibility.md`

## 共享写入权切换 Skill

Codex 与 DeepSeek Harness 共用：

```text
/Users/jiu/.agents/skills/care-agent-writer-handoff/SKILL.md
```

当用户要求切换主实现者，或计划在 Codex 额度恢复后切回来时，调用：

```text
$care-agent-writer-handoff 切换到 DeepSeek Harness
$care-agent-writer-handoff 切换到 Codex
```

该 Skill 会先保存进度、测试结果、未完成事项和唯一下一步，再更新 `docs/08-agent-collaboration.md` 中的 `CURRENT_WRITER`。仅打开项目、额度耗尽或执行只读复核都不会自动转移写入权。

## 共享周分支发布 Skill

Codex 与 DeepSeek Harness 共用：

```text
/Users/jiu/.agents/skills/care-agent-weekly-github-publish/SKILL.md
```

已完成一个周次并需要提交、推送到 GitHub 时调用：

```text
$care-agent-weekly-github-publish 上传周 3 到 GitHub
$care-agent-weekly-github-publish 把当前周 4 阶段提交并推送
```

该 Skill 会核对当前唯一写入者、周次验收、分支基线、`.env` 忽略、暂存文件和密钥模式，正常提交并推送后再比对远端提交。它不会自动合并 `main`、强制推送或伪造历史；周 0–2 因建仓较晚，继续保留为 `main` 的联合基线。

## 给当前主实现者的启动提示词

```text
你是 CareAgent 当前唯一主实现者。请把工作目录切换到：
/Users/jiu/Developer/Projects/Python/care-agent

先只读运行 pwd、git status --short --branch 和 find . -maxdepth 1 -type f -print。
当前目录已是 Git 仓库（origin: github.com/JJJJIU9999/care-agent，默认分支 main）；git status 正常工作。

先完整阅读 README.md、AGENTS.md、task_plan.md、findings.md、progress.md
以及 docs/00-start-here.md 到 docs/09-week0-feasibility.md。

当前唯一任务：按 docs/07-roadmap-and-handoff.md 进入周 4，
先固定 /api/v1/agent/runs 的 SSE 事件协议，
再串联 Java 登录用户、Python RAG 与预约草案。
不要提前接入 Redis、Kubernetes、OCR 或全文检索。

你拥有当前工作树写入权，可以在既定范围内实现、测试和修复；同一时间另一代理不会写入。
开始前检查实际目录和工具版本；完成后更新 progress.md 和 findings.md，
列出运行过的验证命令、结果、未解决问题与下一步。
不要读取展示、打印、复制或提交 .env 的真实值。
```

## 代理分工规则

当前主实现者以 `docs/08-agent-collaboration.md` 的 `CURRENT_WRITER` 为准：

- 主实现者拥有当前工作树的写权限，负责实现、测试、修复和更新进度文档。
- 评审者读取代码、测试和文档，输出按严重程度排序的意见，不直接修改文件。
- 当前主实现者可以持续完成既定 Roadmap；另一代理只读复核。
- 如果切换主实现者，必须调用 `$care-agent-writer-handoff`，先停止原实现者并在 `progress.md` 写明交接点。
- 已完成周次需要发布到 GitHub 时，调用 `$care-agent-weekly-github-publish`；发布本身不改变主实现者。
- 禁止两个工具同时在同一工作树写代码。

原因不是某个工具只能“思考”或只能“执行”，而是设计决策和代码实现需要由同一个主体闭环验证。把大脑与手脚硬拆开，会造成接口理解漂移、半完成修改和责任不清。

## 每次开发的最小闭环

1. 读取当前计划和进度。
2. 检查真实代码和依赖状态。
3. 只完成一个可验证切片。
4. 运行与改动直接相关的测试。
5. 更新 `progress.md`；发现新事实时更新 `findings.md`。
6. 如需改变已锁定决策，先写入决策记录并说明证据。

## 必须停止并报告的情况

- Chat 或 Embedding 接口不可用。
- Python 依赖不支持当前解释器，且尚未选择独立项目版本。
- 文档解析无法保留关键政策条件。
- 数据库迁移会破坏已有数据。
- 同一文件存在另一代理未完成的修改。
- 需求会突破固定 MVP 或打乱砍项顺序。
- 需要真实个人数据、医疗信息或生产密钥。
