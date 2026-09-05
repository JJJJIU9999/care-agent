# 周 0 可行性记录

## 当前结论

状态：周 0 通过，结论为**按计划继续**（进入周 1 最小 CLI 基线）。

- Python 3.14.7 上 FastAPI、Pydantic、Uvicorn 和 OpenAI 客户端安装成功。
- `/health` 单元测试与真实 Uvicorn + curl 均通过。
- 已获得 4 份独立官方候选资料，另有 1 份征求意见稿和 1 份重复附件作为负面/解析样本。
- DeepSeek Chat 真实最小调用已通过；两个本地中文 Embedding 候选已在同一数据集上完成 CPU 实测，并选定 `BAAI/bge-small-zh-v1.5`。
- 已补充并验证 1 份官方纯扫描件（川民发〔2020〕67号，10 页、每页为彩色 JPEG、无文本层）；扫描件策略固定为不做 OCR、人工转 Markdown 并保留原件链接。

周 0 的 Chat、Embedding、中文文档解析和本地工具链均有可重复实测记录，且不存在入口文档列出的停止条件，因此允许进入周 1；RAG 能力仍需周 1 在人工整理的真实 Markdown 切片上验证，不能在此声称已可用。

## 能力到模块与证据的映射

当前没有单独提供目标 JD，以下仅做项目能力映射，不声称匹配某个具体岗位。

| 能力 | 计划模块 | 最终证据 |
|---|---|---|
| MaaS | Python 模型客户端 | 真实 Chat/Embedding 自检记录、错误率、延迟与成本 |
| Agent | 确定性路由与两个受限工具 | 路由、提示注入、禁止自主下单测试 |
| Java | 认证、服务、草案、预约事务 | 幂等、草案唯一消费、防超卖、取消回补测试 |
| Vue | 登录与唯一用户路径 | 浏览器端到端演示与类型检查 |
| Python | FastAPI、解析、Embedding、检索 | pytest、curl、评测报告 |
| SQL | PostgreSQL `app`/`rag` schema | 迁移、约束、并发测试与 `EXPLAIN ANALYZE` |
| 安全 | JWT、内部 Token、信任边界 | 越权、未知字段、提示注入和日志脱敏测试 |
| Docker | 分阶段 Compose | 健康检查与完整环境冒烟测试 |
| CI | 三端测试与镜像构建 | GitHub Actions 运行记录 |

## 本机工具链

| 项目 | 结果 |
|---|---|
| Python | 3.14.7，依赖安装与测试通过 |
| uv | 0.12.7 |
| Java | 17.0.20.1 与 25.0.4.1；默认是 25，Java 阶段须显式选择 17 |
| Maven/Gradle | 均未全局安装；后续使用 Maven Wrapper |
| Node/npm | 22.23.2 / 10.9.8 |
| Docker/Compose | CLI 29.7.2 / 5.4.0；daemon 当前未运行 |

## Python 自检证据

```bash
UV_CACHE_DIR=/private/tmp/care-agent-uv-cache uv sync
UV_CACHE_DIR=/private/tmp/care-agent-uv-cache uv run pytest -q
UV_CACHE_DIR=/private/tmp/care-agent-uv-cache uv run uvicorn care_agent_ai.app:app --host 127.0.0.1 --port 8010
curl --fail --silent --show-error http://127.0.0.1:8010/health
```

结果：4 个最小测试通过；真实健康检查返回 `{"status":"ok"}` 和 HTTP 200。pytest 有一条 Starlette/AnyIO 上游弃用警告，不影响本次结论。

## 模型接口自检

真实值只保存在本机、被 `.gitignore` 排除且权限为 `600` 的 `.env` 中，不把密钥写入文档、日志或聊天：

```bash
set -a
source .env
set +a

UV_CACHE_DIR=.uv-cache uv run python -m care_agent_ai.chat_probe
```

探针只输出模型名、响应字符数和耗时，不输出密钥或完整模型响应。2026-09-05 实测 `deepseek-v4-flash` 成功返回 2 个字符，端到端耗时 `646.44 ms`。DeepSeek 官方当前未提供 Embeddings 接口，因此 Embedding 改为本地模型，不复用或新增远程凭证。

## 本地 Embedding 选型

固定环境：Python 3.14.7、Sentence Transformers 6.0.1、PyTorch 2.14.0、Transformers 5.16.1、CPU、L2 归一化、Top K=5。评测使用同一份 20 条政策片段和 10 道中文问题，其中 7 道库内题有金标准片段。首次下载时间不计入稳定加载延迟，正式结果由缓存后的离线复跑生成。

| 模型 | 版本 | 缓存 | 维度 | Hit@1 | Hit@5 | MRR | 平均查询 | P95 查询 | 错误 | API 成本 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| `BAAI/bge-small-zh-v1.5` | `7999e1d` | 92 MB | 512 | 85.71% | 100% | 90.48% | 3.70 ms | 4.39 ms | 0 | $0 |
| `intfloat/multilingual-e5-small` | `614241f` | 470 MB | 384 | 100% | 100% | 100% | 5.66 ms | 6.21 ms | 0 | $0 |

两者 Hit@5 差距为 0，小于预设的 3 个百分点；按“Hit@5 接近时选择延迟和成本更低者”的规则，MVP 选定 BGE。它的平均查询耗时低约 35%，缓存体积小约 80%，且不产生 API 费用。E5 的 Hit@1 与 MRR 更高，保留为后续扩大真实语料评测时的对照，不写成全面质量结论。

原始结果：

- `data/evaluation/results/week0-bge-small-zh-v1.5.json`
- `data/evaluation/results/week0-multilingual-e5-small.json`
- 语料：`data/evaluation/week0_embedding_corpus.jsonl`

离线复现命令：

```bash
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 UV_CACHE_DIR=.uv-cache \
  uv run python -m care_agent_ai.local_embedding_benchmark --model bge
```

限制：该基准由项目现有政策问题及短片段构造，样本很小且与语料相关性强，仅用于周 0 选型，不能代表开放领域检索表现；周 1 必须在人工整理的真实 Markdown 切片上继续评测。

## 政策资料解析结论

完整 URL、SHA-256、状态和解析决策见 `data/policies/manifest.json`。

- 普通正文 PDF：可自动提取，但需保留页码并抽检特殊标点。
- 六列表格 PDF：纯文本会打乱单元格关系，人工转 Markdown。
- HTML：正文可提取，但必须去掉导航、脚本和重复移动端内容后人工核对。
- 扫描件：已用川民发〔2020〕67号 10 页纯扫描件验证——pypdf 提取文本为 0、每页为单张 2410×3438 彩色 JPEG；判定为无文本层扫描件，固定不做 OCR，人工转 Markdown 并保留原件链接。
- 征求意见稿：不得作为正式政策依据。
- 重复附件：只保留一个知识来源，避免召回重复内容。

首批 7 道库内题、3 道库外题见 `data/evaluation/week0_questions.jsonl`。

## 周 0 结论与收口记录

| 维度 | 结论 |
|---|---|
| 成本 | 本地 Embedding 无 API 费用、缓存约 92 MB；Chat 为按量计费的 DeepSeek API，周 0 仅以最小调用验证可用，不记录精确金额 |
| 延迟 | Chat `deepseek-v4-flash` 端到端 `646.44 ms`；BGE 平均查询 `3.70 ms`、P95 `4.39 ms`、单次探针 `87.4 ms` |
| 兼容性 | Python 3.14.7 依赖安装与测试通过；Java 服务须显式选 Java 17（默认 25）；Maven/Gradle 缺失，后续提交 Maven Wrapper |
| 失败项 | DeepSeek 无官方 Embeddings 接口（已改本地 BGE）；Docker daemon 未运行（不阻断周 0，进入 Compose 前必须解决）；chengdu.gov.cn 主站及多数区县站启用 JS 反爬，扫描件改由可达的官方民政站点取得 |
| 结论 | **按计划继续**，进入周 1 最小 CLI 基线；不采用“Python 降级”或“文档统一人工 Markdown” |

## 未解决问题与停止条件

1. Chat 与本地 Embedding 已解除阻断，MVP 向量维度固定为 512。
2. 官方扫描件样本已验证，扫描件解析策略固定为人工 Markdown（不做 OCR）。
3. Docker daemon 未运行，但不阻断当前纯 Python 自检；进入 Compose 阶段前必须解决。
4. 周 1 之前需先在人工整理的真实 Markdown 切片上复评 Embedding，不把周 0 小样本选型外推为开放领域质量结论。

下一步：进入周 1，人工整理一份 Markdown 政策文本，搭建切片、Embedding、检索与带引用回答的最小 CLI。
