# 周 6 演示视频（3–5 分钟）

## 成品

- 文件：`demo/careagent-week6-demo.webm`（VP8，1440×900，实测时长 194.7 秒 ≈ 3 分 25 秒，无音轨、底部烧录中文字幕，Chrome/Firefox/VLC 直接播放）
- 录制方式：`scripts/week6_demo_video.py` 用 Playwright 无头浏览器驱动**真实运行中的 Compose 栈**（`docker compose up -d --build`，含真实 DeepSeek 模型流式回答），逐帧录屏；不剪辑、不摆拍未实现功能。

## 镜头顺序（与 `docs/06-test-and-evaluation.md` 要求逐项对应）

| 镜头 | 画面 | 演示的契约 |
|---|---|---|
| ① 登录 | demo_user 逐字输入并登录 | 虚构演示账号、短期 JWT、路由守卫 |
| ② 政策问答 | 点击建议问题，流式 token + 内联引用 + 侧栏引用卡 | pgvector 真实检索结果才允许成为引用；citation 含机构/章节/页码/原文 |
| ②c 多轮 | 第二轮追问携带上一组 user/assistant 上下文 | 上下文是客户端提供的不可信文本，只进模型提示，不能触发工具或改变授权 |
| ②b 拒答 | 上海政策问题被地域守卫直接拒答 | 知识库外宁可不说不可编造；同类还有医疗剂量、绕过确认守卫 |
| ③ 服务卡片 + 草案 | “我想预约武侯区的助洁服务”→ service_card + tool_confirmation | Python 只调 Java 只读工具与草案工具；价格/时段来自数据库权威数据；模型只能出草案 |
| ④ 确认下单 | 点击“确认预约” | 前端只提交 `draftId` + `Idempotency-Key`；Java 事务原子消费草案、条件扣容量 |
| ⑤ 我的预约 + 取消 | 状态 CONFIRMED → CANCELLED | 只允许本人一次状态迁移，容量只回补一次 |
| ⑥ 证据摘要页 | 由 `data/evaluation/week6_*.json` 动态生成的 HTML | 50 题评测、注入自检统计、20 路 SSE 计数、并发防超卖、EXPLAIN ANALYZE |

## 复现

```bash
docker compose up -d --build            # 完整栈（含 .env 的模型配置）
set -a; source .env; set +a
UV_CACHE_DIR=.uv-cache uv run --offline python scripts/week6_demo_video.py
# 输出 demo/careagent-week6-demo.webm；脚本会打印会话时长并在 <165s 时告警
```

依赖说明：录制仅新增 dev 依赖 `playwright`（标准库与现有栈无浏览器录屏能力）；浏览器二进制缓存在 `.playwright-browsers/`（已 gitignore），不污染应用镜像。

## 已知限制（如实记录）

- 无旁白音轨，信息通过画面字幕与操作本身传达；按“只做必要剪辑”口径直接交付单镜头连续录制。
- 录制会真实创建并取消一条演示预约（演示数据），每录一次在会话/预约表中新增演示行。
- 镜头内所有账号、服务、预约均为虚构演示数据；未出现任何真实个人信息或密钥。
