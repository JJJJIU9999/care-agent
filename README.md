# CareAgent

CareAgent 是一个面向老人及家属的养老政策问答与上门服务预约 MVP。系统使用 Python RAG 返回可核验的政策引用，并由 Spring Boot 管理服务、预约草案、用户确认、并发容量与取消回补。

## 当前状态

周 0 可行性闸门、周 1 最小 CLI 基线、周 2 FastAPI 导入链路和周 3 Java 主业务均已实现。当前实现：

- 读取一份人工核对 Markdown 政策知识包，本地 BGE 检索，DeepSeek 带可定位引用回答。
- FastAPI 内部接口：`X-Internal-Token` 认证的上传、状态查询与问答封装。
- Compose 启动 PostgreSQL/pgvector 与 Spring Boot；Python FastAPI 仍可单独启动。
- Markdown / 文本 PDF 上传（10 MB + magic bytes 校验）切片、Embedding 并写入 pgvector。
- Spring Security + 短期 JWT、登录限流、JPA/Flyway、服务时段查询、预约草案、幂等确认、原子容量扣减、我的预约、取消回补与关键审计。
- Java 管理员上传/状态接口只做代理，文件内容校验、解析和向量写入仍由 Python 负责。

评测已扩展为 14 道知识库内、6 道知识库外，20/20 行为正确，Hit@5 100%。Java 测试包含 100 个请求竞争容量 10 的防超卖验证；这不是生产吞吐承诺。Vue 尚未接入。详情见 [周 0 可行性记录](docs/09-week0-feasibility.md)、[周 1 评测结果](data/evaluation/week1_results.json) 和 [周 2 评测结果](data/evaluation/week2_results.json)。

## 周 1 CLI

先在项目目录加载本机 `.env`，再提问：

```bash
set -a
source .env
set +a
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run python -m care_agent_ai.rag_cli "四川省高龄津贴面向多少周岁以上老人？"
```

离线复跑检索与拒答评测：

```bash
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run python -m care_agent_ai.week1_evaluation
```

## 周 2 FastAPI 与导入

启动仅含 PostgreSQL/pgvector 的 Compose，并在首次数据卷创建时初始化 `rag` schema：

```bash
docker compose up -d
```

在 `.env` 中补充 `INTERNAL_TOKEN`（内部接口认证）与 `RAG_DATABASE_URL` 后启动服务：

```bash
set -a
source .env
set +a
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run uvicorn care_agent_ai.app:app --host 127.0.0.1 --port 8010
```

内部接口（均需 `X-Internal-Token` 头）：

```bash
# 上传 Markdown / 文本 PDF（multipart：file + title + issuingOrganization + sourceUrl + effectiveDate）
curl -X POST http://127.0.0.1:8010/internal/v1/knowledge/documents \
  -H "X-Internal-Token: $INTERNAL_TOKEN" \
  -F "file=@data/policies/curated/week1-policy-pack.md;type=text/markdown" \
  -F "title=四川省推进基本养老服务体系建设实施方案" \
  -F "issuingOrganization=四川省人民政府办公厅"

# 查询导入状态
curl -H "X-Internal-Token: $INTERNAL_TOKEN" \
  http://127.0.0.1:8010/internal/v1/knowledge/documents/<documentId>

# 同步问答封装（复用周 1 CLI 的检索 + 守卫 + 引用）
curl -X POST http://127.0.0.1:8010/internal/v1/rag/answer \
  -H "X-Internal-Token: $INTERNAL_TOKEN" -H "Content-Type: application/json" \
  -d '{"question":"四川省高龄津贴面向多少周岁以上老年人？"}'
```

周 2 离线评测（14 库内 + 6 库外）：

```bash
HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 uv run python -m care_agent_ai.week1_evaluation \
  --questions data/evaluation/week2_questions.jsonl \
  --output data/evaluation/week2_results.json
```

## 周 3 Java 主业务

在 `.env` 中设置至少 32 字节的 `JWT_SECRET`，并确保 `INTERNAL_TOKEN` 与 Python 使用同一个值。启动数据库后，可用 Java 17 运行：

```bash
docker compose up -d postgres
cd java-service
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw spring-boot:run
```

也可以从项目根目录启动数据库和 Java 容器：

```bash
docker compose up -d --build
```

Java 镜像构建使用仓库内 `java-service/docker-maven-settings.xml`，仅把 Maven Central 映射到国内镜像；它不会修改本机全局 Maven 配置。

虚构演示账号：`demo_user / demo-user-2026`（USER）和 `demo_admin / demo-admin-2026`（ADMIN）。不要在生产环境复用这些账号或默认 Compose 密钥。

运行 Java 单元与集成测试（需要 Docker）：

```bash
cd java-service
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./mvnw test
```

## MVP 唯一路径

```text
登录 → 政策问答（带引用）→ 服务卡片 → 预约草案
    → 用户确认 → 服务端重新校验并下单 → 我的预约
```

## 文档

- [开发入口与阅读顺序](docs/00-start-here.md)
- [产品需求与范围](docs/01-product-requirements.md)
- [系统架构与数据流](docs/02-architecture.md)
- [API 与 SSE 契约](docs/03-api-contract.md)
- [数据模型与状态机](docs/04-data-model.md)
- [安全与信任边界](docs/05-security.md)
- [测试与评测计划](docs/06-test-and-evaluation.md)
- [周计划、砍项与交接](docs/07-roadmap-and-handoff.md)
- [Codex 与 DeepSeek Harness 协作规则](docs/08-agent-collaboration.md)

## 技术参考

项目仅借鉴 [RAG_Techniques](https://github.com/NirDiamant/RAG_Techniques) 中 Simple RAG、Reliable RAG 的技术思路，不复制其 Notebook 代码或项目结构。参考仓库采用自定义非商业许可证；本项目代码需独立实现，并保留技术来源说明。

## 事实纪律

- 计划中的功能不是已实现功能。
- 性能、检索提升和并发数字必须来自可重复测试。
- “100 并发”只能表述为并发竞争与防超卖测试，不能写成生产级高并发能力。
