# Codex 与 DeepSeek Harness 协作规则

## 当前写入权

```text
CURRENT_WRITER: Codex
```

该行是当前写入权的唯一状态源。未被列出的代理一律只读；创建 Skill、打开项目、额度耗尽或发起只读复核均不自动改变它。

## 共享切换 Skill

Codex 与 DeepSeek Harness 都从以下共享位置发现切换流程：

```text
/Users/jiu/.agents/skills/care-agent-writer-handoff/SKILL.md
```

用户明确指定接收方后调用：

```text
$care-agent-writer-handoff 切换到 DeepSeek Harness
$care-agent-writer-handoff 切换到 Codex
```

Skill 要求交出方先停止新增修改，运行与当前切片直接相关的验证，在 `progress.md` 记录完成内容、文件、测试、失败项和唯一下一步，最后才更新 `CURRENT_WRITER`。交接完成后，原写入者立即转为只读。

## 共享周分支发布 Skill

Codex 与 DeepSeek Harness 都从以下共享位置发现 GitHub 周分支发布流程：

```text
/Users/jiu/.agents/skills/care-agent-weekly-github-publish/SKILL.md
```

调用示例：

```text
$care-agent-weekly-github-publish 上传周 3 到 GitHub
$care-agent-weekly-github-publish 把当前周 4 阶段提交并推送
```

只有顶部 `CURRENT_WRITER` 指定的代理可以执行发布。Skill 会验证周次验收、真实分支基线、测试证据、`.env` 忽略、暂存内容、密钥模式和远端提交；不自动合并、强推、删除分支或转移写入权。周 0–2 没有独立 Git 快照，保持为 `main` 联合基线，不创建误导性的历史分支。

## 结论

Codex 和 DeepSeek Harness 可以随额度与任务阶段轮换主实现权。工具角色不代表能力高低；关键约束仍是同一时间只有一个主体写当前工作树，主实现者必须同时负责实现、验证和进度记录。

## 为什么主线交给一个工具

- 架构决策、代码实现和测试结果由同一个上下文闭环。
- 避免计划与真实代码之间反复转述造成信息损失。
- 避免两个代理在同一工作树覆盖文件。
- 故障发生时能追踪是谁修改、运行了什么、下一步是什么。
- CareAgent 有跨 Java、Python、Vue 的契约，一个主线更容易保持一致。

[OpenAI 官方模型指导](https://developers.openai.com/api/docs/guides/latest-model)建议长任务明确结果、成功标准、约束、测试和停止条件，并持续保存长任务的重要状态。CareAgent 的文档包、`progress.md` 和单一写入者规则正是为此服务。

## 当前主实现者的职责

- 每次先读取入口、计划、发现和进度文件。
- 只执行 `task_plan.md` 中当前周尚未完成的最小切片。
- 修改前核对真实代码、依赖和当前工作目录。
- 修改后运行直接相关的测试或检查，并把命令和结果写入 `progress.md`。
- 新事实写入 `findings.md`；范围或架构变化必须先说明证据。
- 不打印、复制、提交或在回复中展示 `.env` 的值。

当前主实现者可以在上述范围内自主实现和修复，不需要每一步都切换代理确认。

## 首次从 Codex 交接到 Harness（历史记录）

当前交接点已经满足：

1. Codex 完成周 0 Chat 与本地 Embedding 切片后停止写入。
2. `progress.md`、`findings.md` 和 `docs/09-week0-feasibility.md` 已保存实际结果。
3. Harness 接手后的唯一任务是补充并验证 1 份官方扫描件样本，完成周 0 结论。
4. Harness 必须先确认目录为 `/Users/jiu/Developer/Projects/Python/care-agent`。
5. 当前目录不是 Git 仓库；`git status` 报错是已知现状，不得擅自执行 `git init`。

此后每次切换均以共享 Skill 和顶部 `CURRENT_WRITER` 为准。需要复核时，用户应明确写“只读复核”；需要另一代理修复时，应调用 Skill 明确转移写入权。

## 本机 Harness 现状

当前缓存包实测为 `@deepseek-ai/dsh 0.1.2-rc.1`，属于 release candidate。它通过以下脚本启动：

```text
/Users/jiu/Developer/Tools/DeepSeekHarness/Harness_start.command
```

该脚本默认进入：

```text
/Users/jiu/Developer/Sandbox
```

而 CareAgent 位于：

```text
/Users/jiu/Developer/Projects/Python/care-agent
```

因此 Harness 每次参与项目前必须先切换并只读确认：

```bash
cd /Users/jiu/Developer/Projects/Python/care-agent
pwd
git status --short --branch
find . -maxdepth 1 -type f -print
```

`pwd` 不匹配时禁止修改文件。项目已于 2026-09-05 初始化并推送到 `github.com/JJJJIU9999/care-agent`（默认分支 `main`）；`git status --short --branch` 现在可正常确认工作树与上游状态。

## 防冲突规则

- 同一时间只有一个 `WRITER`。
- `REVIEWER` 不运行格式化、代码生成、迁移或自动修复命令。
- 不共享未说明的临时文件和终端状态。
- 切换 WRITER 前先记录 Git 状态和未跟踪文件。
- 不用“两个工具都改一次，最后挑好的”处理同一工作树。
- 如需真正并行，必须使用独立 Git worktree 和不同任务边界；MVP 阶段默认不并行。

## 推荐节奏

```text
当前 WRITER 完成一个最小切片
  → 运行测试并更新 progress.md / findings.md
  → 用户可继续使用当前 WRITER，或调用切换 Skill
  → 周次完成后可调用 GitHub 发布 Skill
  → 接收方复核交接点与实际文件后继续
```

## 给 Codex 的只读复核模板

```text
评审范围：
当前提交或 Git 状态：
已运行测试：
必须保持的产品不变量：
禁止修改的目录：
请只读检查：
输出格式：P0/P1/P2、文件位置、复现方法、最小修复建议。
```

## 工具选择不是简历证据

简历只描述最终系统、本人理解并能解释的代码和实测结果。无论代码最初由哪个代理生成，只要本人无法解释、复现或修复，就不能当作已经掌握的项目能力。
