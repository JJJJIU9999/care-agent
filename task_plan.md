# CareAgent 文档准备计划

## 目标

在开始业务代码前，形成一套可供新对话直接读取并实施的项目文档。文档必须固定 MVP 边界、接口、数据状态、安全约束、测试口径和代理协作规则。

## 当前阶段

- [x] 核对项目目录与 Python 学习现状
- [x] 固定最终 MVP 与第二轮评审补丁
- [x] 建立项目入口、规则和规划文件
- [x] 完成产品、架构、接口与数据文档
- [x] 完成安全、测试、周 0 与交接文档
- [x] 校验文档链接、术语和约束一致性

## 文档准备阶段当时不在范围

- 不创建 Spring Boot、FastAPI 或 Vue 源码
- 不安装依赖
- 不启动数据库或容器
- 不下载政策文档
- 不填写未经测试的简历指标

## 完成标准

1. 新对话按 `docs/00-start-here.md` 的顺序即可理解项目。
2. 实现者无需重新决定 MVP、ORM、数据库、上下文边界或预约状态机。
3. 关键写操作均有信任边界、事务和失败行为。
4. 每周都有可验证的停止条件和砍项规则。

## 结果

文档包已完成。下一次开发从 `docs/07-roadmap-and-handoff.md` 的周 0 开始。

## 周 0 可行性闸门（已完成）

- [x] 读取开发入口、现有计划与项目规则
- [x] 完整读取产品、架构、接口、数据、安全、测试、路线图与协作文档
- [x] 核对本机 Java、Python、Node、Docker 与构建工具版本
- [x] 建立独立虚拟环境并实测 FastAPI `/health`
- [x] 为 Chat 与 Embedding 留下不接触真实密钥的最小测试和探针
- [x] 实测 Chat 接口最小调用
- [x] 实测中文 Embedding 候选与基本质量
- [x] 下载并核验 4 份独立官方候选资料，记录 URL 与 SHA-256
- [x] 建立 7 道库内、3 道库外首批评测题
- [x] 补充并验证 1 份官方扫描件样本
- [x] 记录成本、延迟、兼容性、失败项与继续/停止结论

### 周 0 完成标准

只有 Chat、Embedding、中文文档解析和本地工具链均有可重复的实测记录，且不存在入口文档列出的停止条件，才允许进入周 1；周 0 期间不创建三端业务脚手架。

### 周 0 当前执行顺序

1. 核对工具链与可用凭证名称（不输出凭证值）。
2. 选择依赖实际支持的 Python 版本，建立独立虚拟环境。
3. 实现并测试最小 `/health`，用真实进程和 curl 验证。
4. 在已有可用凭证前提下实测 Chat 与两个中文 Embedding 候选；无凭证则明确阻断。
5. 从政府权威来源选择政策样本，记录元数据、哈希与解析结论。

## Codex / Harness 写入权切换 Skill（已完成）

- [x] 核对现有单一写入者约束、交接文档与 Harness Skill 发现位置
- [x] 创建两边共享的 `care-agent-writer-handoff` Skill
- [x] 在 `AGENTS.md`、开发入口和协作文档中登记调用方式与唯一状态源
- [x] 校验 Skill 格式、文档引用和现有测试

## Codex / Harness 周分支发布 Skill（已完成）

- [x] 创建双方共享的 `care-agent-weekly-github-publish` Skill
- [x] 固定唯一写入者、验收、密钥扫描和远端提交校验流程
- [x] 固定周分支必须累计前一已验收阶段，不隐式合并或强推
- [x] 记录周 0–2 为 `main` 联合基线，禁止伪造历史分支
- [x] 在 `AGENTS.md`、开发入口和协作文档登记调用方式
- [x] 运行 Skill 格式校验与项目文档一致性检查

## 周 1 最小 CLI 基线（已完成）

- [x] Codex 接管写入权并核对周 0 交接状态
- [x] 同步 README 与开发入口的周 1 状态
- [x] 人工整理 1 份带来源元数据和可定位章节的 Markdown 政策文本
- [x] 实现最小切片、BGE Embedding、Top K 检索和带引用回答 CLI
- [x] 为切片、引用约束和库外拒答留下最小测试
- [x] 用既有 7 道库内、3 道库外问题评测行为、Hit@5、延迟与失败原因
- [x] 运行完整回归并更新周 1 证据与下一步

### 周 1 验收

10 道题至少 8 道行为正确；每个有效回答都带可定位引用。周 1 不接 FastAPI、Java、PostgreSQL 或全文检索。

## 周 2 FastAPI 与导入（已完成）

- [x] 确认 Docker daemon 并启动仅含 PostgreSQL/pgvector 的 Compose
- [x] 用 SQL 初始化 `vector` 扩展和 `rag` schema（三张表 + HNSW 索引）
- [x] FastAPI 封装周 1 CLI 能力（`/internal/v1/rag/answer`）
- [x] 内部上传与状态查询接口（`/internal/v1/knowledge/documents`）
- [x] Markdown、文本 PDF、10 MB 限制与 magic bytes 校验
- [x] 评测集扩展为 14 道知识库内、6 道知识库外

### 周 2 验收

14 道库内 Hit@5 100%、6 道库外全部正确拒答，20/20 行为正确；上传/状态/问答接口通过内部 Token 认证，Markdown/文本 PDF 导入写入 pgvector。周 2 不接 Java、Vue、Alembic 或全文检索。

## 周 3 Java 主业务（已完成）

- [x] 从 `main` 创建独立 `week3` 分支并保留交接记录
- [x] 核对 Java 17、Maven Wrapper 和周 3 API/数据/安全契约
- [x] 建立最小 Spring Boot 3、JPA 与 Flyway 基线
- [x] 实现 Spring Security、JWT 登录和登录限流
- [x] 实现服务、时段种子数据与只读查询
- [x] 实现预约草案、确认幂等、原子容量扣减、取消状态守卫与容量回补
- [x] 保留登录、下单和取消三类审计记录
- [x] 实现管理员上传代理接口，不复制 Python 导入逻辑
- [x] 运行单元、数据库和并发预约验证并更新证据

### 周 3 边界

只实现路线图列出的 Java 主业务闭环；不开发 Vue、服务管理 CRUD、通用审计框架、Redis、Kubernetes、OCR 或全文检索。最终预约只信任 `draftId`、当前认证用户和 `Idempotency-Key`。

## 错误记录

| 日期 | 问题 | 处理 |
|---|---|---|
| 2026-09-05 | 本机没有全局 `dsh` 命令 | 使用现有 `Harness_start.command` 通过 `npx @deepseek-ai/dsh web` 启动 |
| 2026-09-05 | `care-agent` 当前不是 Git 仓库 | 记录现状；周 0 先完成可行性验证，不擅自初始化仓库 |
| 2026-09-05 | `uv python list --only-installed` 无法访问用户缓存中的 `.git` | 后续把 `UV_CACHE_DIR` 指向项目内目录，避免扩大权限 |
| 2026-09-05 | Docker CLI 已安装但 daemon 未运行 | 周 0 记录为工具链未完全就绪；当前 FastAPI 自检不依赖 Docker |
| 2026-09-05 | 沙箱内 `uv sync` 因 DNS 解析失败 | 经联网授权后重试成功，已锁定并安装 30 个包 |
| 2026-09-05 | 沙箱内 Uvicorn 无权绑定 `127.0.0.1:8010`，沙箱内 curl 也无法访问该端口 | 经最小本机网络授权后，服务与 curl 均成功；验证结束后已停止进程 |
| 2026-09-05 | 首次严格解析评测 JSONL 时末尾空行触发 `JSONDecodeError` | 删除唯一空行，保留标准的单行单对象格式后重新验证 |
| 2026-09-05 | macOS `textutil` 在受限环境中无法连接辅助应用读取 HTML | 改用只读 `xmllint --html --xpath` 核对正文，不修改原始文件 |
| 2026-09-05 | 第一次从 manifest 拼接 `shasum -c` 输入格式错误 | 改用 `jq` 结构化生成标准校验行，6 个文件全部通过 |
| 2026-09-05 | `uv` 默认用户缓存目录在沙箱内不可访问 | 继续使用项目内 `UV_CACHE_DIR=.uv-cache`，不扩大文件权限 |
| 2026-09-05 | 首次环境变量状态检查的一行 Python 命令转义错误 | 改用字符串拼接后通过；未输出任何凭证值 |
| 2026-09-05 | 两次登记切换 Skill 的补丁遗漏协作文档标题下的现有模式说明，导致上下文匹配失败 | 未产生部分修改；读取准确上下文后改用逐文件最小补丁 |
| 2026-09-05 | `quick_validate.py` 使用系统及 Codex 捆绑 Python 时均缺少 `yaml` 模块 | 不给 CareAgent 增加无关依赖；通过 `PYTHONPATH` 复用项目 uv 缓存中的 PyYAML，原校验器通过 |
| 2026-09-05 | 共享 Skill 的 `user-invocable` 可被 Harness 接受但不在 Codex 校验器允许字段内 | 删除该可选字段；两边省略时均默认允许调用 |
| 2026-09-05 | 沙箱内调用 DeepSeek 时 DNS 解析失败 | 经最小联网授权后重试成功；确认不是鉴权或模型错误 |
| 2026-09-05 | `uv add sentence-transformers` 报告一个上游依赖的无效版本说明 | `uv` 自动移除多余引号并成功解析、安装；以实际导入与测试结果为准 |
| 2026-09-05 | BGE 缓存复跑仍尝试联网检查可选配置并触发 DNS 重试 | 中止等待，使用 `HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1` 后离线复跑成功 |
| 2026-09-05 | 记忆中曾记录另一个 `DeepSeekHarness/start.command` 路径，但现场文件不存在 | 以现场存在的 `DeepSeekHarness/Harness_start.command` 为准，不创建或覆盖用户启动脚本 |
| 2026-09-05 | 周 1 首轮地域正则把“省本级……成都市”误判为库外地名，评测为 9/10 | 支持地域出现时优先放行，再对其他明确行政区问题拒答；新增回归测试后为 10/10 |
| 2026-09-06 | 周 3 启动时默认 Java 为 25，且没有全局 `mvn` | 所有 Java 构建显式使用 Temurin 17，并提交 Maven Wrapper，不修改系统默认 Java |
| 2026-09-06 | 受限环境内 `docker info` 无权访问 Docker socket | 先按环境限制记录；需要数据库集成验证时仅对 Docker 命令申请最小授权 |
| 2026-09-06 | Python 虚拟环境没有 `bcrypt`，不能用它生成演示密码哈希 | 不增加 Python 依赖；使用系统 `htpasswd` 生成虚构演示账号的 BCrypt 哈希，并由 Spring Security 验证 |
| 2026-09-06 | Maven Central 在本机和容器内多次 TLS 超时 | 本地验证先使用 `/private/tmp` 设置；Java 镜像再使用仓库内仅镜像 `central` 的可审计设置，未修改用户全局 Maven，依赖坐标保持不变 |
| 2026-09-06 | 第一版测试代码的 Java 文本块语法不合法 | 改为普通 JSON 字符串，未引入额外测试工具 |
| 2026-09-06 | 服务查询使用可空 JPQL 参数时 PostgreSQL 报 `42P18` | 拆成四个明确的 Spring Data 查询方法，避免未定类型的空参数 |
| 2026-09-06 | 受限环境首次无法访问本机 Docker socket | 以最小本机权限复跑 Testcontainers；真实 PostgreSQL 集成测试 6/6 通过 |
| 2026-09-06 | Docker CLI 凭证助手在非交互环境中挂起 | 使用 `/private/tmp` 下的空 Docker 配置并直接调用 Compose 插件，不修改用户 Docker 配置 |
| 2026-09-06 | Python 回归首次无法写默认 `uv` 缓存 | 继续使用项目内 `UV_CACHE_DIR=.uv-cache` 并离线运行，34/34 通过 |
