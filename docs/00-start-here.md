# 开发入口

## 项目状态

截至 2026-09-05，CareAgent 的周 0 可行性闸门、周 1 最小 CLI 基线和周 2 FastAPI 与导入链路均已通过。周 2 已用 FastAPI 封装周 1 CLI 能力，Compose 只启动 PostgreSQL/pgvector 并初始化 `rag` schema，实现带 `X-Internal-Token` 的内部上传与状态查询接口（Markdown、文本 PDF、10 MB 与 magic bytes 校验），并把评测集扩展到 14 道库内、6 道库外（20/20 行为正确，Hit@5 100%）。当前下一步是按路线图进入周 3：Spring Security/JWT、JPA/Flyway、服务与时段种子、预约草案与确认事务。

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

## 给当前主实现者的启动提示词

```text
你是 CareAgent 当前唯一主实现者。请把工作目录切换到：
/Users/jiu/Developer/Projects/Python/care-agent

先只读运行 pwd、git status --short --branch 和 find . -maxdepth 1 -type f -print。
当前目录已是 Git 仓库（origin: github.com/JJJJIU9999/care-agent，默认分支 main）；git status 正常工作。

先完整阅读 README.md、AGENTS.md、task_plan.md、findings.md、progress.md
以及 docs/00-start-here.md 到 docs/09-week0-feasibility.md。

当前唯一任务：按 docs/07-roadmap-and-handoff.md 进入周 3，
先核对本机 Java 17 与 Maven Wrapper 计划，再实现 Spring Security/JWT、JPA/Flyway、
服务与时段种子、预约草案与确认事务。
不要提前接入 Vue、Redis、Kubernetes、OCR 或全文检索。

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
