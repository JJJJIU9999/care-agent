# CareAgent 进度

## 2026-09-05

- 已完成两轮计划评审并固定 MVP、砍项顺序和事务边界。
- 已核对 CareAgent 为空目录，尚未开始业务开发。
- 已核对 PythonStudy 当前处于工程基础早期阶段。
- 已开始创建供新对话读取的开发文档包。
- 已完成产品范围、架构、API、数据模型、安全、测试、周计划和代理协作文档。
- 已固定 Codex 为推荐主实现者、DeepSeek Harness 为只读评审者的工作方式。
- 已检查全部文档入口链接、首行标题和关键约束；未发现缺失的本地链接。

## 下一步

- 新开发对话从周 0 开始，未通过闸门前不得生成三端脚手架。

## 2026-09-05 周 0 开发启动

- 已读取 `docs/00-start-here.md`、项目规则、现有计划、README、产品、架构和 API 文档。
- 已运行规划会话恢复检查；没有发现需恢复的未同步上下文。
- 已运行 `git status --short --branch`；结果：当前目录不是 Git 仓库。
- 当前正在完整读取其余设计文档，尚未创建任何业务脚手架或安装依赖。
- 已完整读取 `docs/01-product-requirements.md` 至 `docs/08-agent-collaboration.md`；周 0 边界、停止条件和通过标准已确认。
- 下一步唯一任务：核对本机工具链与可用模型凭证名称，然后建立最小 Python 可行性验证。
- 已核对工具链：Python 3.14.7、uv 0.12.7、Java 17/25、Node 22、Docker/Compose CLI 可用；Maven/Gradle 缺失，Docker daemon 未运行。
- 已只检查模型凭证环境变量名称，当前进程未发现可用 Chat/Embedding 凭证；没有读取或输出任何密钥值。
- 工具错误：`uv python list --only-installed` 因用户缓存目录权限失败；下一步改用项目内 `UV_CACHE_DIR`。
- 已创建周 0 最小 Python 可行性代码：FastAPI `/health`、Embedding 探针及两个测试；未创建 Java/Vue/数据库脚手架。
- 已用 Python 3.14.7 创建 `.venv` 并生成 `uv.lock`；首次 `uv sync` 因沙箱 DNS 失败，经联网授权重试后成功安装依赖。
- 本次新增：`.gitignore`、`.python-version`、`pyproject.toml`、`uv.lock`、`src/care_agent_ai/*`、`tests/test_week0_python.py`。
- 已运行 `uv run pytest -q`：2 个测试全部通过，耗时 0.49 秒；有 1 条上游弃用警告。
- 首次启动 Uvicorn 因沙箱禁止绑定本机端口失败；经授权后在 `127.0.0.1:8010` 成功启动。
- 已运行 `curl --fail --silent --show-error http://127.0.0.1:8010/health`：返回 `{"status":"ok"}`，服务日志为 HTTP 200；随后已正常停止临时服务。
- 下一步唯一任务：从政府权威来源选择 3–5 份成都养老政策样本并验证可获取性与解析类型。
- 已检索政府权威来源，初步确认 4 个样本方向：长期护理保险普通 PDF、四川基本养老服务实施方案复杂排版 PDF、基本养老服务清单表格 PDF、成都养老服务措施网页。
- 尚未找到并验证官方扫描件，继续检索；不会用第三方转载替代权威原文。
- 已下载 3 份官方 PDF 和 1 份官方 HTML 到 `data/policies/raw/`，并记录每份文件的 SHA-256。
- 已用 `file` 核验三份 PDF 的文件类型及 HTML 的 UTF-8 文本类型；下一步按 PDF 检查流程提取并逐页渲染。
- 已使用项目外的捆绑 PDF 运行时分别提取并渲染三份 PDF，共 23 页；临时产物位于 `tmp/pdfs/week0-policy-*`。
- 已通过 `pdfinfo` 核验页数、A4 页面、无加密；三份 PDF 均存在文本层，不属于纯扫描件。
- 已逐页视觉检查全部 23 页并与抽取文本对照：未发现裁切或重叠；发现 1 份征求意见稿、1 组重复附件、表格错序风险及特殊标点抽取问题。
- 当前政策样本尚未达到可入库的 3–5 份独立正式文件，需要继续补充官方正式来源和扫描件样本。
- 已新增官方长期护理保险政策解读和成都失能老年人养老消费补贴试点网页，当前形成 4 份独立候选来源。
- 已新增 Chat 自检探针、空值环境变量示例、政策 manifest、7+3 评测题和 `docs/09-week0-feasibility.md`。
- 首次严格解析评测 JSONL 因文件末尾空行失败；已删除该空行，等待复验。
- 已复验：pytest 3 个测试通过；Python 编译通过；manifest JSON 有效；评测题严格解析为 7 道库内和 3 道库外；本地文档链接存在。
- SHA 首次校验命令格式错误，改用 `jq` 生成校验输入后 6 个原始政策文件全部通过。
- 已更新开发入口和 README，使状态从“代码未开工”准确变为“周 0 最小 FastAPI 已通过、业务代码未开工”。
- 当前阶段：周 0 可行性闸门进行中。
- 完成内容：本机工具链、Python 3.14 兼容性、FastAPI 健康检查、Chat/Embedding 探针、4 份独立官方候选资料、政策 manifest、7+3 评测题。
- 失败或未解决问题：没有模型凭证，未完成真实 Chat/Embedding 和两个中文 Embedding 候选比较；没有合适的官方扫描件；Docker daemon 未运行。
- 下一步唯一任务：用户在本机终端准备供应商环境变量后，完成真实模型调用与 Embedding 对比。
- 已清理本次 PDF 检查生成的 23 张临时页面图片与抽取文本；原始政策文件保留在 `data/policies/raw/`。
- 最终本地验证：`uv lock --check` 通过；pytest 3/3 通过；manifest JSON 有效；6 个 SHA-256 全部匹配；评测题文件严格为 10 行；临时 PDF 目录已清理。
- 当前工作树状态：目录仍不是 Git 仓库，无法提供 `git status`；未擅自执行 `git init`。
- 已确认本地 `.env` 的 API Key、Base URL 和 Chat 模型均已设置，Embedding 留空；全过程未输出密钥值。
- 首次真实 Chat 请求在沙箱内因 DNS 受限失败，经最小联网授权后成功。
- DeepSeek `deepseek-v4-flash` 最小调用实测通过：返回 2 个字符，耗时 646.44 ms；周 0 Chat 闸门已完成。
- 状态文档更新后运行 `UV_CACHE_DIR=.uv-cache uv run pytest -q`：3 个测试全部通过，保留 1 条上游弃用警告。
- 下一步唯一任务：选取并实测两个本地中文 Embedding 候选，完成基本质量、延迟与资源占用比较。
- 已根据模型官方说明选定 `BAAI/bge-small-zh-v1.5`（512 维）和 `intfloat/multilingual-e5-small`（384 维）作为同批中文检索对照；下一步验证 Sentence Transformers/PyTorch 在 Python 3.14 上能否安装运行。
- 已新增唯一必要依赖 `sentence-transformers`，锁定后实际安装 `sentence-transformers 6.0.1`、`torch 2.14.0` 和 `transformers 5.16.1`；Python 3.14.7 导入成功。
- 依赖安装后原有 pytest 仍为 3/3 通过；当前 PyTorch 未启用 MPS，后续 Embedding 指标按 CPU 记录。
- 已新增 20 条周 0 Embedding 评测片段和可复现基准脚本；新增纯指标测试后 pytest 为 4/4 通过。
- 已下载并固定 BGE `7999e1d` 与 E5 `614241f`，首次联网运行和缓存后离线复跑均成功；因缓存复跑默认仍发 HEAD 请求，显式启用 Hugging Face/Transformers 离线模式。
- 正式对比：BGE 与 E5 的 Hit@5 均为 100%；按预设规则选取平均查询更快、缓存更小的 BGE，MVP 向量维度固定为 512。
- 已将远程 Embedding 探针改为本地 BGE 探针，实测输出 512 维、单次 87.4 ms；未使用 DeepSeek Key。
- 原始结果已保存到 `data/evaluation/results/`，包含精确模型版本、数据集哈希、依赖版本、参数、逐题排名和耗时。
- 下一步唯一任务：补充并验证 1 份官方扫描件样本，完成周 0 继续/停止结论。
- 用户已明确将 CareAgent 唯一主实现权从 Codex 转移给 DeepSeek Harness；本次交接文档更新完成后，Codex 停止写入并默认只读复核。
- 已核对 Harness 启动器、缓存版本和 3080 监听状态；启动器默认目录仍为 Sandbox，因此在开发入口中加入了 CareAgent 绝对路径和首轮只读确认命令。
- 已更新 `AGENTS.md`、开发入口和代理协作规则，写明 Harness 的职责、当前唯一任务、验收记录要求、无 Git 的已知现状和密钥保护要求。
- Harness 接管后的唯一任务保持不变：补充并验证 1 份官方扫描件样本，完成周 0 继续/停止结论。
- 交接文档一致性检查完成；历史角色决策已标注为被新决定替代，pytest 复验 4/4 通过并保留 1 条上游弃用警告。
- 周 0 收口：已补充并验证 1 份官方纯扫描件（川民发〔2020〕67号，10 页、每页彩色 JPEG、无文本层），并完成继续/停止结论。
- 扫描件来源：达州市民政局官网转发的《四川省社区养老服务综合体建设导则（试行）》；文件 `data/policies/raw/07-community-elderly-care-complex-guide-scan.pdf`，SHA-256 `e8a4420e2a212fa947e78326f36ee4abc402f4151d0bf031c26a62820e17e44d`。
- 新增 `src/care_agent_ai/scan_check.py`（`is_scan`/`inspect_pdf`）与 `tests` 中 `test_is_scan_requires_images_and_no_text`；新增 dev 依赖 `pypdf` 用于区分扫描件与文本 PDF。
- 更新 `data/policies/manifest.json`（7 份文档，JSON 有效）、`findings.md`、`docs/09-week0-feasibility.md` 与 `task_plan.md`。
- 验证命令与结果：`UV_CACHE_DIR=.uv-cache uv run pytest -q` 5/5 通过（保留 1 条上游弃用警告）；`jq empty data/policies/manifest.json` 通过；`inspect_pdf(...)` 输出 `pages=10, textChars=0, images=10, isScan=True`。
- 周 0 结论：**按计划继续**，进入周 1 最小 CLI 基线；未解决项仅 Docker daemon 未运行（CLI 阶段不需要）。
- 下一步唯一任务：进入周 1——人工整理一份 Markdown 政策文本，搭建切片、Embedding、检索与带引用回答的最小 CLI，并在真实切片上复评 BGE。
- 当前工作树状态：目录仍不是 Git 仓库；未执行 `git init`。本次扫描件候选临时产物已清理。
- 已创建 Codex 与 DeepSeek Harness 共用的 `/Users/jiu/.agents/skills/care-agent-writer-handoff/SKILL.md`，用于在用户明确指定接收方后安全转移唯一写入权。
- 已在 `AGENTS.md`、`docs/00-start-here.md` 和 `docs/08-agent-collaboration.md` 登记 Skill 路径、调用方式、交接检查点及唯一状态源；当前 `CURRENT_WRITER` 仍为 `DeepSeek Harness`，本次没有执行写入权切换。
- Skill 校验：复用本地已有 PyYAML 运行 `quick_validate.py`，结果为 `Skill is valid!`；未给 CareAgent 新增依赖。
- 文档校验：三个入口文件均能检索到 `care-agent-writer-handoff`，且协作文档只有一条 `CURRENT_WRITER` 状态行。
- 回归验证：`UV_CACHE_DIR=.uv-cache HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run pytest -q` 为 5/5 通过，保留 1 条既有 Starlette/AnyIO 上游弃用警告。
- 下一步唯一任务保持不变：进入周 1——人工整理一份 Markdown 政策文本，搭建切片、Embedding、检索与带引用回答的最小 CLI，并在真实切片上复评 BGE。

## 2026-09-05 写入权交接

- `WRITER_HANDOFF: DeepSeek Harness -> Codex`
- 已完成工作：周 0 四项可行性闸门全部通过；Harness 补充并验证官方纯扫描件，新增可重复的扫描件判定与测试，结论为进入周 1。
- Harness 阶段新增或修改的主要文件：`data/policies/raw/07-community-elderly-care-complex-guide-scan.pdf`、`data/policies/manifest.json`、`src/care_agent_ai/scan_check.py`、`tests/test_week0_python.py`、`pyproject.toml`、`uv.lock`、`docs/09-week0-feasibility.md`、`task_plan.md`、`findings.md` 和 `progress.md`。
- 交接验证：`UV_CACHE_DIR=.uv-cache HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run pytest -q` 为 5/5 通过，保留 1 条既有 Starlette/AnyIO 上游弃用警告。
- 已知状态：当前目录不是 Git 仓库，`git status --short --branch` 返回 exit 128；未执行 `git init`。Docker daemon 未运行，但周 1 CLI 阶段不依赖 Docker。`docs/00-start-here.md` 的项目状态与 Harness 首条提示仍停留在扫描件完成前，需要在周 1 启动时同步。
- 下一步唯一任务：由 Codex 进入周 1 最小 CLI 基线，先同步过期入口状态，再人工整理一份 Markdown 政策文本，实现切片、BGE 检索与带引用回答，并在真实切片上复评 BGE。

## 2026-09-05 周 1 启动

- 已运行规划会话恢复检查；没有发现需恢复的未同步上下文。
- 已确认 `CURRENT_WRITER: Codex`，并读取项目规则、交接记录和周 1 路线图。
- 已把 `task_plan.md` 的周 0 标记为完成，并新增周 1 最小 CLI 的实现与验收清单。
- 已同步 `README.md` 与 `docs/00-start-here.md`，移除“扫描件仍待完成”的过期状态，并把启动提示改为跟随当前主实现者。
- 下一步唯一任务：选择并人工整理一份真实政策 Markdown，固定其来源元数据、章节与可回答范围。

## 2026-09-05 周 1 完成

- 已新增 `data/policies/curated/week1-policy-pack.md`：一份人工核对 Markdown，按 7 个独立片段保留四份原始政策来源的文档名、机构、日期、URL 和页码/章节。
- 已重新提取并逐页视觉核对 `02-basic-elderly-care-plan.pdf` 全部 14 页；三条表格事实定位到第 13–14 页。另三份 HTML 的四条事实已与政府网页原文逐项核对。
- 已新增 `src/care_agent_ai/rag_cli.py`：Markdown 切片、本地 BGE、Top 5 检索、0.5 最低相关度、地域/医疗/预约守卫、DeepSeek 回答和只返回模型实际引用来源。
- 已新增 `src/care_agent_ai/week1_evaluation.py` 和 `tests/test_week1_rag.py`；评测结果保存为 `data/evaluation/week1_results.json`。
- 首轮真实评测为 9/10；原因是地域正则误伤“省本级……成都市”问题。完成根因修复并增加回归测试后复跑为 10/10。
- 最终 BGE 真实切片结果：Hit@1 100%、Hit@5 100%、MRR 100%；索引 97.21 ms，平均问题检索/守卫 2.70 ms，P95 4.50 ms，失败原因为空。
- DeepSeek 端到端复验成功：回答四川高龄津贴面向 80 周岁及以上老年人，并且只返回实际使用的第 14 页、附件第 19 项官方来源引用；本次问答阶段实测 1018.90 ms。
- 最终回归：`UV_CACHE_DIR=.uv-cache HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run pytest -q` 为 14/14 通过，保留 1 条既有上游弃用警告。
- 周 1 验收已通过；下一步唯一任务：进入周 2，先确认 Docker daemon，再用 FastAPI 封装现有 CLI 能力并建立最小导入链路。

## 2026-09-05 写入权交接

- `WRITER_HANDOFF: Codex -> DeepSeek Harness`
- 已完成工作：周 1 最小 CLI 基线已验收；人工政策知识包、BGE Top 5 检索、DeepSeek 带引用回答、三类拒答守卫和 7+3 离线评测均已落地。
- Codex 阶段新增或修改的主要文件：`data/policies/curated/week1-policy-pack.md`、`data/evaluation/week1_results.json`、`src/care_agent_ai/rag_cli.py`、`src/care_agent_ai/week1_evaluation.py`、`tests/test_week1_rag.py`、`README.md`、`docs/00-start-here.md`、`task_plan.md`、`findings.md` 和 `progress.md`。
- 交接验证：`UV_CACHE_DIR=.uv-cache HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run pytest -q` 为 14/14 通过，保留 1 条既有 Starlette/AnyIO 上游弃用警告；`UV_CACHE_DIR=.uv-cache uv lock --check` 通过；`week1_results.json` 为 10/10 行为正确、Hit@5 100%、失败原因空。
- 已知状态：目录仍不是 Git 仓库，`git status --short --branch` 返回 exit 128，未执行 `git init`；Docker daemon 当前未运行，连接 `/Users/jiu/.docker/run/docker.sock` 失败；`.env` 已存在但本次交接未读取或输出任何值。
- 下一步唯一任务：由 DeepSeek Harness 进入周 2，先启动并确认 Docker daemon，再用 FastAPI 封装现有周 1 CLI 能力并建立 Markdown/文本 PDF 的最小安全导入链路。

## 2026-09-05 周 2 启动与实现

- 已核对 `CURRENT_WRITER: DeepSeek Harness`（`docs/08-agent-collaboration.md` 第 6 行）与最新交接记录 `WRITER_HANDOFF: Codex -> DeepSeek Harness`，确认本会话拥有唯一写入权。
- 已只读确认 `pwd` 为 `/Users/jiu/Developer/Projects/Python/care-agent`；`git status --short --branch` 返回 exit 128（目录不是 Git 仓库，已知现状，未执行 `git init`）。
- 已启动 Docker Desktop：`open -a Docker` 后 `docker info` 返回 server 29.7.2，daemon 已运行，周 2 首个闸门通过。
- 新增 `compose.yaml`：只启动 `pgvector/pgvector:pg16`，本地开发口令用 `${POSTGRES_PASSWORD:-careagent_local_dev}` 覆盖，数据卷首次创建时执行 `db/init`。
- 新增 `db/init/001-rag-schema.sql`：`CREATE EXTENSION vector`、`CREATE SCHEMA rag`，以及 `knowledge_document`、`document_chunk`（512 维 HNSW 索引）、`ingestion_job` 三张表；Alembic 按路线图延后。
- 新增依赖 `psycopg[binary]>=3.3.5` 与 `python-multipart>=0.0.32`，并把 `pypdf` 从 dev 组移入主依赖（`ingest.py` 运行时解析 PDF）。
- 新增 `src/care_agent_ai/config.py`：`INTERNAL_TOKEN` 无默认值（未配置时内部接口一律 401），`token_is_valid` 用 `hmac.compare_digest` 常量时间比较，每次读取环境变量。
- 新增 `src/care_agent_ai/db.py`：psycopg3 连接、`knowledge_document`/`ingestion_job`/`document_chunk` 写入、`similarity_search`（`1 - (embedding <=> query)` 余弦相似度）。
- 新增 `src/care_agent_ai/ingest.py`：`validate_upload`（扩展名白名单 → 声明 MIME → 10 MB → magic bytes → 内容非空）、`chunk_markdown`/`chunk_pdf_text`（保留页码）、`parse_document`；纯扫描件抛 `SCAN_REQUIRES_MANUAL_MARKDOWN` 不调 OCR。
- 新增 `src/care_agent_ai/rag_service.py`：惰性加载 BGE 与周 1 知识包、`answer_question` 复用 rag_cli、`ingest_document` 完成 PENDING→PROCESSING→COMPLETED|FAILED 并写入 pgvector，SHA-256 去重。
- 扩展 `src/care_agent_ai/app.py`：`/internal/v1/knowledge/documents`（上传）、`/internal/v1/knowledge/documents/{documentId}`（状态）、`/internal/v1/rag/answer`（问答封装），统一 `X-Internal-Token` 认证与 `{code,message}` 错误体。
- 扩展 `src/care_agent_ai/week1_evaluation.py`：`evaluate` 接受 `--questions`，结果记录 `questionsFile`。
- 新增 `data/evaluation/week2_questions.jsonl`：14 道库内 + 6 道库外（沿用周 1 的 7+3 并新增 7 库内、3 库外）。
- 新增测试 `tests/test_week2_ingest.py` 与 `tests/test_week2_api.py`：上传校验、Markdown/文本 PDF 切片、扫描件拒收、内部 Token、接口拒绝路径。
- 验证：`UV_CACHE_DIR=.uv-cache HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run pytest -q` 为 33/33 通过，保留 1 条既有 Starlette/AnyIO 上游弃用警告。
- 离线评测：`week2_questions.jsonl`（14 库内 + 6 库外）20/20 行为正确，Hit@1/Hit@5/MRR 均为 100%，索引约 85–108 ms、平均每题约 2.7 ms、P95 约 3.9–4.3 ms；结果保存到 `data/evaluation/week2_results.json`。
- 已拉取并启动 `pgvector/pgvector:pg16`（640 MB），`docker compose up -d` 后容器 healthy。
- SQL 初始化验证：`vector` 扩展存在，`rag` schema 含 `knowledge_document`/`ingestion_job`/`document_chunk` 三张表，`embedding` 列类型为 `vector(512)`。
- 真实 Uvicorn + curl 冒烟：`/health` 200；无 Token 401；空问题 422 `INVALID_QUESTION`；`.txt` 415 `UNSUPPORTED_FILE_TYPE`；伪造 PDF 422 `INVALID_MAGIC_BYTES`。
- 端到端上传（`X-Internal-Token` + multipart）：Markdown 上传返回 202 `COMPLETED` + `documentId`，状态查询返回 `COMPLETED`；重复上传同一文件返回同一 `documentId`（SHA-256 去重）。
- 切片入库验证：Markdown 切成 2 段（`高龄津贴`、`家庭适老化改造`），`vector_dims(embedding)=512`；文本 PDF 按页切片且 `page_number` 保留（1、2、3…）；纯扫描件返回 `FAILED`，`failure_reason` 为“纯扫描件不做 OCR”。
- pgvector 相似度检索验证：查询“高龄津贴面向多少周岁”命中“高龄津贴”切片（相似度 0.75），其次为 PDF 第 3 页与“家庭适老化改造”，证明上传 → 切片 → Embedding → pgvector 写入 → 余弦检索整条链路可用。
- 验证后已停止临时 Uvicorn 与 Compose 容器；数据卷 `care-agent_pgdata` 保留，可 `docker compose up -d` 恢复。
- 下一步唯一任务：按路线图进入周 3——Spring Security/JWT、JPA/Flyway、服务与时段种子、预约草案与确认事务（含原子消费、幂等、容量扣减、取消回补）。
- 当前工作树状态：目录仍不是 Git 仓库，`git status --short --branch` 返回 exit 128，未执行 `git init`。
