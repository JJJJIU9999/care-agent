# CareAgent 调研结论

## 已确认事实

- 项目目录在 `/Users/jiu/Developer/Projects/Python/care-agent`，文档创建前为空。
- 当前 PythonStudy 尚未开始 HTTP、FastAPI、Pydantic、异步和并发阶段。
- 本机当前 `python3` 为 3.14.7，项目依赖兼容性必须在周 0 实测；必要时为 CareAgent 选择受依赖支持的独立 Python 版本。
- 本机没有全局 Maven 命令，后续应优先提交 Maven Wrapper，或通过容器构建 Java 服务。
- DeepSeek Harness 通过 `/Users/jiu/Developer/Tools/DeepSeekHarness/Harness_start.command` 启动，并在 `/Users/jiu/Developer/Sandbox` 中运行。
- 本机缓存的 `@deepseek-ai/dsh` 版本为 `0.1.2-rc.1`；它不是全局命令，且默认启动目录不是 CareAgent。

## 已锁定决策

- MVP 唯一路径：登录 → 带引用政策问答 → 服务卡片 → 预约草案 → 确认下单 → 我的预约。
- Java 使用 Spring Boot 3、Java 17、JPA，不混用 MyBatis。
- Python 使用 FastAPI 和直接的 OpenAI-compatible 客户端，不引入 Agent 框架。
- PostgreSQL + pgvector；MVP 不使用 Redis、Kubernetes、OCR、复杂多轮记忆。
- 只允许一个代理作为主实现者修改项目；第二个代理默认只读评审。
- 历史上曾推荐 Codex 负责主线、DeepSeek Harness 只读评审；该角色安排已于 2026-09-05 被用户明确替换为 Harness 主实现、Codex 按需复核。单一写入者规则继续有效。

## 需要周 0 用实测确定

- CareAgent 的 Python 版本及依赖兼容性
- 可用的 Chat 与中文 Embedding 接口
- 两个中文 Embedding 候选模型的效果、延迟和成本
- 3–5 份成都养老政策文档的解析策略

## 2026-09-05 周 0 启动发现

- `care-agent` 当前没有 `.git`，`git status` 返回“not a git repository”；未执行初始化。
- 入口、产品、架构和 API 文档一致要求：周 0 未通过前不得生成 Spring Boot、FastAPI 或 Vue 脚手架。
- 最终预约只能由 Java 根据 `draftId`、当前认证用户和 `Idempotency-Key` 创建；模型及客户端字段均不可信。
- 周 0 的最小可写范围是 Python 独立虚拟环境、FastAPI `/health`、Chat/Embedding 自检、中文政策样本与验证记录；三端完整脚手架仍禁止创建。
- 周 0 只有三种允许结论：按计划继续、Python 降级但保留 Java 主路径、文档统一人工 Markdown。
- Embedding 选型须用同一批中文问题比较两个当前可调用模型；Hit@5 差距小于 3 个百分点时再比较延迟和成本。
- 本机可用：Python 3.14.7、uv 0.12.7、Node 22.23.2、npm 10.9.8、Docker CLI 29.7.2、Compose 5.4.0。
- 本机有 Java 17.0.20.1 和 Java 25.0.4.1；当前默认是 Java 25，后续 Java 服务必须显式选择 Java 17。
- Maven 与 Gradle 均未全局安装；Java 阶段应提交 Maven Wrapper，不依赖全局 Maven。
- Docker daemon 当前未运行；模型相关环境变量名称检查没有发现可用变量。
- Python 3.14.7 已实际解析并安装 FastAPI 0.141.1、Pydantic 2.13.5、Uvicorn 0.52.4、OpenAI 3.8.0、pytest 9.1.1；依赖安装阶段未出现解释器兼容错误。
- FastAPI 最小测试与真实 Uvicorn 进程均在 Python 3.14.7 上成功；`GET /health` 实际返回 HTTP 200 和 `{"status":"ok"}`。
- pytest 出现一条 Starlette 对 AnyIO 类型别名的上游弃用警告，不影响本次功能；不为此修改或降级依赖。
- 本机 `.env` 中的 DeepSeek Chat 配置已验证为完整：API Key、Base URL 和 Chat 模型均已设置，Embedding 有意留空；检查过程只输出 set/empty 状态。
- 真实 DeepSeek Chat 最小调用通过：`deepseek-v4-flash` 返回 2 个字符，端到端耗时 `646.44 ms`，证明当前 OpenAI-compatible 客户端、网络、凭证和模型名可用。
- DeepSeek 官方当前未提供 Embeddings 接口，因此同一 DeepSeek 凭证不能解除向量模型阻塞；后续优先比较两个本地中文 Embedding 候选，避免为周 0 再引入第二个平台凭证。
- 本地 Embedding 周 0 候选固定为 `BAAI/bge-small-zh-v1.5` 与 `intfloat/multilingual-e5-small`：二者均为 MIT 许可并支持 Sentence Transformers；前者中文专用、512 维、模型权重约 95.8 MB，后者支持中文等多语言、384 维、模型权重约 471 MB。
- BGE 检索应只给短 query 添加中文检索指令，passage 不加；E5 检索必须分别添加 `query: ` 与 `passage: ` 前缀。两者均做 L2 归一化后用点积得到余弦排序。
- Sentence Transformers 官方建议检索任务使用 `encode_query()`/`encode_document()`；为确保旧模型提示词行为明确，周 0 基准将显式按各模型卡添加前缀并调用统一的 `encode(..., normalize_embeddings=True)`。
- Python 3.14.7 已实际安装并导入 `sentence-transformers 6.0.1`、`torch 2.14.0`、`transformers 5.16.1`；依赖解析时仅出现上游无效版本说明的自动修正警告，安装成功。
- 当前 PyTorch 构建 `torch.backends.mps.is_available()` 为 `False`，周 0 本地 Embedding 基准只能标为 CPU 实测，不能声称 Apple GPU 加速。
- 两个模型已固定到公开仓库提交：BGE `7999e1d3359715c523056ef9478215996d62a620`，E5 `614241f622f53c4eeff9890bdc4f31cfecc418b3`；正式结果可在模型缓存存在时完全离线复跑。
- 同一份 20 条片段、10 道问题（7 道库内金标准）的结果：BGE Hit@1 85.71%、Hit@5 100%、MRR 90.48%、平均查询 3.70 ms、P95 4.39 ms；E5 Hit@1/Hit@5/MRR 均为 100%、平均查询 5.66 ms、P95 6.21 ms。
- 两者 Hit@5 相同，按预设规则比较延迟与成本后选择 BGE：本地 API 成本均为 0，BGE 缓存约 92 MB、E5 约 470 MB，BGE 平均查询快约 35%。这只是项目内小样本选型，不外推为开放领域质量结论。
- MVP 本地 Embedding 固定为 `BAAI/bge-small-zh-v1.5`、512 维、CPU；E5 保留为扩大真实语料后的对照候选。
- 用户因 Codex 可用额度和连续开发体验，明确把 CareAgent 唯一主实现权转给 DeepSeek Harness；Codex 此后默认只读复核，只有用户明确再次转移写入权时才修改。
- 现场核对的 Harness 启动器是 `/Users/jiu/Developer/Tools/DeepSeekHarness/Harness_start.command`，缓存版本 `0.1.2-rc.1`，启动器仍默认进入 `/Users/jiu/Developer/Sandbox`；交接提示必须显式切换到 CareAgent。
- 2026-09-05 交接时 `127.0.0.1:3080` 已有 Harness 进程监听；这是瞬时状态，后续仍以实际端口检查为准。
- Codex 与当前 `dsh` Harness 都能发现用户级 `~/.agents/skills`；切换 Skill 固定安装为 `/Users/jiu/.agents/skills/care-agent-writer-handoff/SKILL.md`，避免 Harness 从 Sandbox 启动时依赖项目 cwd 才能发现。
- 写入权的唯一状态源固定为 `docs/08-agent-collaboration.md` 中的 `CURRENT_WRITER`；交接历史、测试结果和下一步继续追加到 `progress.md`，不再在多个入口文件重复维护当前代理名称。
- 为兼容两边的 Skill frontmatter，未保留 Harness 支持但 Codex 校验器拒绝的可选 `user-invocable` 字段；省略该字段时两边仍允许用户调用。

## 周 0 政策样本候选

- 四川省医保局的省本级参保人员参加成都市长期护理保险通知为 6 页官方 PDF，网页解析可稳定提取段落和页码，可作为普通文本 PDF。
- 四川省政府《四川省推进基本养老服务体系建设实施方案》为 14 页官方 PDF，能提取文本但出现全角数字及特殊标点编码，可作为复杂排版样本。
- 上述实施方案的《四川省基本养老服务清单》附件为 3 页官方表格 PDF，列顺序在纯文本提取中容易破碎，可作为表格样本。
- 成都市《关于加快推进养老服务发展若干措施》官方政务服务网页正文清晰，可作为人工核对后的 Markdown 来源候选。
- 官方扫描件已于 2026-09-05 补齐：川民发〔2020〕67号《四川省社区养老服务综合体建设导则（试行）》，经达州市民政局官方站点转发的 10 页纯扫描件，无文本层。

## 周 0 已下载政策样本

- `01-long-term-care-insurance.pdf`：四川省医疗保障局，省本级基本医保参保人员参加成都市长期护理保险相关通知，SHA-256 `83d6ddba27112f96925a4f37cc7e8005cef03c80295589972af7a67e0b1607ca`。
- `02-basic-elderly-care-plan.pdf`：四川省人民政府办公厅《四川省推进基本养老服务体系建设实施方案》，SHA-256 `1622676c81f13f0d83a38c91b3ec3ea534726b035474c6b30abb89c62fd51a59`。
- `03-basic-elderly-care-list.pdf`：四川省基本养老服务清单表格附件，SHA-256 `3e6f28213274287dc7b15d1f107586fc01fba43d653fa3cd05588aed2d8c6ed6`。
- `04-chengdu-elderly-care-measures.html`：成都市人民政府办公厅《关于加快推进养老服务发展若干措施》网页，SHA-256 `7a5f24ddb54bdebf2d61427e7eadd707d75b7cba7146f55daad6cbb0dea98116`。
- 以上均从四川省医保局、省政府或成都市政务服务网直接下载；第三方转载仅用于发现线索，不作为入库来源。
- 本地元数据核验：长期护理保险 PDF 6 页、实施方案 PDF 14 页、服务清单 PDF 3 页；三份均为 A4、未加密。
- 三份 PDF 都能提取出大量文本（约 3.2 万、7.5 万、1.6 万字符，包含分页标记），因此都不是纯扫描件。
- 逐页视觉核验发现 `01-long-term-care-insurance.pdf` 标题明确写有“征求意见稿”；即使来自政府站点，也不能作为最终有效政策回答语料，应从入库候选中剔除或仅作负面解析样本。
- `02-basic-elderly-care-plan.pdf` 14 页均清晰完整，无裁切或重叠；前 11 页连续正文适合自动解析，12–14 页是六列表格，纯文本顺序会破坏单元格对应关系，表格部分应人工转 Markdown。
- `03-basic-elderly-care-list.pdf` 与 `02` 的 12–14 页内容完全同源，是独立附件而非新增政策；适合专门验证表格解析，但不能作为另一份独立知识来源重复入库。
- 抽取结果中的 `ꎬ`、`«»` 等字符与页面视觉标点不一致，说明自动提取后仍需字符规范化和人工抽检。
- 当前已形成 4 份独立官方候选资料：四川基本养老服务实施方案、成都养老服务发展措施、长期护理保险正式政策解读、成都失能老年人养老消费补贴试点动态；征求意见稿和重复附件不计入 4 份。
- HTML 可用 `xmllint` 提取正文，但会混入导航、脚本和重复内容，因此当前仍采用“抽取后人工转 Markdown 并核对”的策略。
- 目标 JD 未单独提供，周 0 仅完成技术能力到模块和证据的映射，不声称匹配具体岗位。

## 2026-09-05 扫描件样本补充与验证

- 成都市政府主站（chengdu.gov.cn）及多数区县站（青白江、成华、东部新区、民政、人社等）启用了 JS 反爬挑战，curl 直接抓取只得到空壳页面；可达的官方站点主要是成都医保局（cdyb.chengdu.gov.cn）、崇州、金牛、天府新区及四川若干地市/县民政站点。
- 对可达站点红头文件批量探测发现，2023 年后的成都市级红头 PDF 均为带文本层的原生电子文件（提取字符几百到上万），不能当扫描件；扫描件须严格满足“无文本层 + 有栅格图像”。
- 已下载并核验川民发〔2020〕67号扫描件：达州市民政局官网（mzj.dazhou.gov.cn）转发的《四川省社区养老服务综合体建设导则（试行）》，10 页、PDF 1.4、每页为单张 2410×3438 彩色 JPEG、pypdf 提取文本为 0。
- 文件 `data/policies/raw/07-community-elderly-care-complex-guide-scan.pdf`，SHA-256 `e8a4420e2a212fa947e78326f36ee4abc402f4151d0bf031c26a62820e17e44d`。
- 该样本是四川省级养老政策（川民发），与现有 3 份四川省级样本同属“成都及四川养老政策”口径；其解析决策固定为 MANUAL_MARKDOWN（不做 OCR），保留原件链接。
- 新增 `src/care_agent_ai/scan_check.py`：`is_scan` 判定“有图像且文本层字符数低于阈值”，`inspect_pdf` 用 pypdf 输出页数、文本字符、图像数与扫描标记；配套测试固化该规则，使扫描判定可重复。
- 新增唯一依赖 `pypdf`（dev 组）用于区分扫描件与文本 PDF；stdlib 无 PDF 解析能力，poppler 需系统安装，故选纯 Python 的 pypdf。

## 周 0 结论

- 四个闸门均已通过实测：Chat 可用、本地中文 Embedding 可用并选型 BGE、中文文档解析覆盖正文/表格/网页/扫描件四类、本地工具链可重复。
- 入口文档列出的停止条件均未触发，结论为**按计划继续**，进入周 1 最小 CLI 基线；不采用 Python 降级或文档统一人工 Markdown 两个备选结论。
- 未解决项仅剩 Docker daemon 未运行（周 1 CLI 阶段不需要，进入 Compose 前必须解决），以及周 1 需在人工整理的真实 Markdown 切片上复评 Embedding。

## 2026-09-05 周 1 启动决策

- 周 1 严格限定为单份人工核对 Markdown 的本地 CLI：切片、既有 BGE 模型、Top K 检索、带引用回答与 7+3 评测。
- 周 1 不接 FastAPI 业务接口、Java、Vue、PostgreSQL、pgvector 或全文检索；先证明最小 RAG 行为正确。
- 验收口径固定为 10 道题至少 8 道行为正确；知识库内有效回答必须带可定位引用，知识库外问题必须拒答。

## 2026-09-05 周 1 结果

- 一份 `data/policies/curated/week1-policy-pack.md` 包含 7 个独立检索片段；每条均保留原始文档、发布机构、日期、原件链接及页码或章节定位。
- PDF 三条事实已逐页复核原始 14 页文件：家庭适老化改造位于第 13 页，社区养老综合服务和高龄津贴位于第 14 页；其余四条分别核对三个政府网页正文。
- BGE 在真实人工片段上的 7 道库内题均为 Top 1，Hit@1、Hit@5、MRR 均为 100%；这是 7 条小规模固定评测集结果，不外推为开放领域效果。
- 3 道库外题分别由地域范围、医疗安全和预约确认守卫拒答；首轮地域正则误伤省本级问题，修复并增加回归测试后 10/10 行为正确。
- 离线检索评测记录：索引 97.21 ms，平均每题 2.70 ms，P95 4.50 ms；这些数字不包含模型加载和 DeepSeek 回答时间。
- DeepSeek 端到端复验成功：回答“80 周岁及以上”，并且只返回实际使用的第 14 页附件第 19 项引用；本次问答阶段耗时 1018.90 ms。
- 当前结果满足周 1 验收；下一步按路线图进入周 2，先确认 Docker daemon，再封装 FastAPI 和最小导入链路。

## 2026-09-05 周 2 实现决策

- 周 2 的问答封装（`/internal/v1/rag/answer`）继续走内存检索，复用周 1 rag_cli 的守卫 + Top K + 带引用回答；pgvector 先用于导入链路的存储与相似度查询。pgvector 正式接入 Agent 检索放到周 4 的 SSE `agent/runs` 流程，符合路线图“FastAPI 封装 CLI 能力”与“内部上传和状态查询接口”两条拆分及最小正确实现原则。
- 上传采用同步处理：请求内完成 `PENDING → PROCESSING → COMPLETED | FAILED` 状态机与 SHA-256 去重。API 契约中“202 PENDING + GET 轮询”面向的是周 3 Java 代理的公开管理员接口；周 2 内部接口先同步，异步后台任务留到周 3 接入真实 worker 时实现。
- 内部 Token 不提供代码默认值（未配置时内部接口一律 401），避免把服务凭证写进仓库；`RAG_DATABASE_URL` 与 compose 的本地开发口令仅作为本机演示默认，生产必须覆盖。
- Python 3.14.7 已安装 `psycopg 3.3.5`（含 `psycopg-binary`）与 `python-multipart 0.0.32`；`pypdf` 因运行时解析 PDF 从 dev 组移入主依赖。
- magic bytes 用手工校验（PDF 以 `%PDF-` 开头、Markdown 为不含 NUL 的 UTF-8 文本），不引入 `python-magic`，避免额外的 libmagic 系统依赖。
- 纯扫描件在 `chunk_pdf_text` 阶段以 `SCAN_REQUIRES_MANUAL_MARKDOWN` 标记任务 FAILED，不调用 OCR；扩展名/MIME/大小/magic 校验则在建文档行之前以 4xx 拒绝。
- 已知限制：内部上传的 Token 校验在 FastAPI 依赖链中执行，未能在读取 multipart body 之前截断超大请求；周 3 Java 管理员代理会先做权限与大小检查再转发。
- 周 2 评测集中 `w2-out-02` 最初用“养老机构消防验收材料”被误召回（topScore 0.587 → 误判 ANSWER_WITH_CITATION），换成“居民用电阶梯电价”后正确拒答；最终 14 库内 Hit@5 100%、6 库外全部正确拒答，20/20。

## 2026-09-06 周 3 启动约束

- Java 公开预约确认接口只接受 `draftId`，幂等键只从 `Idempotency-Key` 请求头取得；未知的价格、用户、服务、时段或状态字段必须被 DTO 拒绝。
- 确认事务顺序固定为：按用户和幂等键查重 → 原子消费本人未过期草案 → 校验启用服务和对应时段 → 条件扣减容量 → 读取数据库现价创建预约 → 写审计；任一步失败整体回滚。
- 取消只允许本人 `CONFIRMED → CANCELLED`；只有状态更新成功时才回补一次容量并写审计，重复取消返回已有状态。
- 周 3 管理员上传仅负责 ADMIN 授权、大小/文件名初检和调用 Python 内部接口；不得在 Java 重写 PDF/Markdown 解析与 Embedding。
- 登录限流只用单进程内存固定窗口，键为规范化 IP 与 username，10 分钟最多 5 次失败；Redis 不进入 MVP。
- 本机 Java 17.0.20.1 可用，但默认 `java` 是 25.0.4.1；周 3 的所有 Maven 命令必须显式设置 `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`。
- 本机仍无全局 Maven；采用提交 Maven Wrapper 的项目内方案，不安装全局 Maven。
- Spring Boot 3 系列当前官方维护线选择 `3.5.16`：官方要求 Java 17+、Maven 3.6.3+，与项目已锁定的 Java 17 一致；不升级到 Spring Boot 4。
- JWT 使用 Spring Security Resource Server 与其自带 Nimbus JOSE 支持，密码使用 Spring Security BCrypt，不另引入第三方 JWT 或密码库。

## 2026-09-06 周 3 实现结论

- Java 服务已固定为 Java 17 + Spring Boot 3.5.16 + JPA + Flyway；仓库提交 Maven Wrapper，不要求本机安装全局 Maven。
- `app` schema 由两条 Flyway 迁移创建和填充：用户、服务、时段、草案、预约和关键审计表；Hibernate 只做 schema 校验，不自动改表。
- 登录使用 BCrypt 与 HS256 JWT；失败限流按规范化 IP + username 做单进程固定窗口，成功登录会清除该键的失败计数。
- 预约确认事务采用数据库条件更新：先按用户与幂等键查重，再原子消费本人未过期草案、校验服务和时段、条件扣减余量、按数据库现价建单并写审计；并发失败整体回滚。
- 取消在锁定本人预约后只允许一次 `CONFIRMED → CANCELLED`；只有首次状态迁移会回补容量和写审计，重复取消只返回现有结果。
- 100 个草案并发竞争容量 10 的真实 PostgreSQL 测试中，成功数严格为 10、余量为 0、预约行数为 10，没有超卖。
- 管理员知识上传仅做角色、文件名、扩展名和 10 MB 大小初检，再以内部 Token 转发给 Python；MIME、magic bytes、解析、切片与 Embedding 仍由 Python 负责。
- PostgreSQL 对可空 JPQL 参数出现 `42P18 could not determine data type`；最终使用四个明确查询方法覆盖筛选组合，避免数据库猜测空参数类型。
- 真实本地 HTTP 冒烟已验证登录、服务与时段、草案、首次确认 201、同键重放 200、未知价格字段 400、取消两次只回补一次，以及三类审计记录。
- 最终 Java 测试为 6/6（含管理员 RBAC、JWT、请求 ID、限流、幂等、取消和并发）；Python 回归为 34/34，均无失败。
- Java 多阶段镜像构建成功；Compose 中 PostgreSQL 为 healthy，Java `/health` 返回 `{"status":"ok"}`。验证后停止 Java 容器释放 8080，保留此前已运行的 PostgreSQL。

## 2026-09-06 GitHub 周分支发布 Skill

- Codex 与 DeepSeek Harness 共同发现用户级 `/Users/jiu/.agents/skills`；周分支发布 Skill 固定安装为 `care-agent-weekly-github-publish/SKILL.md`。
- 发布操作继续受 `CURRENT_WRITER` 约束；发布完成周次不会自动转移写入权，也不会隐式合并 `main`。
- 周分支采用累计检查点：新周必须包含前一已验收周；若 `main` 尚未包含前一周，则从前一周分支创建，不从旧 `main` 制造缺失代码的平行分支。
- Git 在周 2 完成后才初始化，因此周 0–2 没有独立精确快照；Skill 明确禁止创建内容相同或近似重建的误导分支。
