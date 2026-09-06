"""CareAgent 周 6：3–5 分钟演示视频录制（Playwright 无头浏览器 + 页面录屏）。

录制真实浏览器对 Compose 栈（http://127.0.0.1:8088）的完整 MVP 路径：
登录 → 政策问答（流式 + 引用）→ 服务卡片 → 草案 → 确认下单 → 我的预约 → 取消回补，
最后打开由周 6 证据 JSON 生成的评测/测试摘要页。画面底部叠加字幕说明步骤。

前置：`docker compose up -d --build` 已就绪；本机 .env 已加载（政策流需要真实模型）。
输出：demo/careagent-week6-demo.webm（VP8，浏览器直接播放）。
"""

from __future__ import annotations

import json
import shutil
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_URL = "http://127.0.0.1:8088"
VIDEO_DIR = ROOT / "tmp/demo-video"
OUTPUT = ROOT / "demo/careagent-week6-demo.webm"
SUMMARY_PAGE = ROOT / "tmp/week6_demo_summary.html"

import os

os.environ.setdefault("PLAYWRIGHT_BROWSERS_PATH", str(ROOT / ".playwright-browsers"))

CAPTION_JS = """
var __capStyle = document.createElement('style');
__capStyle.textContent = '#__capbar{position:fixed;left:0;right:0;bottom:0;z-index:99999;background:rgba(23,32,29,.92);color:#fdfbf6;font-size:17px;line-height:1.5;padding:12px 22px;font-family:system-ui,PingFang SC,sans-serif;border-top:2px solid #0e7a6b;white-space:pre-wrap;pointer-events:none}';
var __capBar = document.createElement('div');
__capBar.id = '__capbar';
__capBar.textContent = 'CareAgent \u00b7 \u5468 6 \u6f14\u793a\uff1a\u767b\u5f55 \u2192 \u653f\u7b56\u95ee\u7b54\uff08\u5e26\u5f15\u7528\uff09\u2192 \u670d\u52a1\u5361\u7247 \u2192 \u8349\u6848\u786e\u8ba4 \u2192 \u6211\u7684\u9884\u7ea6 \u2192 \u53d6\u6d88\u56de\u8865';
function __capMount() {
  if (!document.documentElement || document.getElementById('__capbar')) return;
  document.documentElement.appendChild(__capStyle);
  document.documentElement.appendChild(__capBar);
}
window.__cap = function (text) { __capMount(); var bar = document.getElementById('__capbar'); if (bar) bar.textContent = text; };
document.addEventListener('DOMContentLoaded', __capMount);
__capMount();
"""

def build_summary_page() -> None:
    evaluation = json.loads((ROOT / "data/evaluation/week6_results.json").read_text(encoding="utf-8"))
    injection = json.loads((ROOT / "data/evaluation/week6_prompt_injection_results.json").read_text(encoding="utf-8"))
    sse = json.loads((ROOT / "data/evaluation/week6_sse_results.json").read_text(encoding="utf-8"))
    sql_md = (ROOT / "data/evaluation/week6_sql_explain.md").read_text(encoding="utf-8")
    sql_head = "\n".join(sql_md.splitlines()[:46])
    summary = evaluation
    html = f"""<!doctype html><html lang="zh"><head><meta charset="utf-8">
<title>CareAgent 周 6 证据摘要</title><style>
body{{font-family:system-ui,'PingFang SC',sans-serif;background:#f7f3ea;color:#292a26;margin:0;padding:48px 64px;font-size:17px;line-height:1.7}}
h1{{font-size:30px}} h2{{font-size:22px;margin-top:36px;border-left:5px solid #0e7a6b;padding-left:12px}}
table{{border-collapse:collapse;margin:12px 0}} td,th{{border:1px solid #d8d2c2;padding:6px 14px}}
pre{{background:#fff;border:1px solid #d8d2c2;padding:14px;border-radius:8px;white-space:pre-wrap}}
.badge{{display:inline-block;background:#0e7a6b;color:#fff;border-radius:8px;padding:2px 12px;margin-right:8px}}
</style></head><body>
<h1>周 6 证据摘要（本机可复现）</h1>
<h2>1. 固定评测：35 库内 + 15 库外</h2>
<p><span class="badge">{summary['behaviorCorrect']}/{summary['questionCount']} 行为正确</span>
Hit@1 {summary['hitAt1']:.0%} · Hit@5 {summary['hitAt5']:.0%} · MRR {summary['mrr']:.2f} ·
平均 {summary['averageQuestionMs']} ms/题 · P95 {summary['p95QuestionMs']} ms</p>
<p>该评测集仅包含 50 道由项目作者依据当前政策文档构造的问题，与语料相关性较强，不能代表开放领域表现。</p>
<h2>2. 提示注入自检（独立统计，不与 RAG 准确率混算）</h2>
<pre>{json.dumps(injection['summary'], ensure_ascii=False, indent=2)}</pre>
<h2>3. 20 路 SSE 与断连取消测试</h2>
<pre>{json.dumps(sse['summary'], ensure_ascii=False, indent=2)}</pre>
<p>Java 终止日志：{sse['logVerification']['windowJavaTerminal']}；Python 终止日志：{sse['logVerification']['windowPythonTerminal']}</p>
<h2>4. 并发竞争与防超卖（口径：非生产高并发）</h2>
<p>100 个草案并发确认竞争容量 10 的时段：成功严格 10、剩余容量 0、预约行数 10
（<code>Week3IntegrationTest.oneHundredConcurrentConfirmationsCannotOversellCapacityTen</code>，Java 套件 10/10）。</p>
<h2>5. SQL 执行计划（EXPLAIN ANALYZE 摘要）</h2>
<pre>{sql_head}</pre>
</body></html>"""
    SUMMARY_PAGE.parent.mkdir(parents=True, exist_ok=True)
    SUMMARY_PAGE.write_text(html, encoding="utf-8")


def pause(page, seconds: float) -> None:
    time.sleep(seconds)


def type_like(page, selector: str, text: str, per_char: float = 0.07) -> None:
    """逐字呈现的打字效果；直接赋值 + input 事件，绕开 CDP 逐键在 el-input 上双写的问题。"""
    page.locator(selector).click()
    for index in range(1, len(text) + 1):
        page.eval_on_selector(
            selector,
            "(el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })); }",
            text[:index],
        )
        time.sleep(per_char)


def record() -> Path:
    from playwright.sync_api import sync_playwright

    VIDEO_DIR.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            record_video_dir=str(VIDEO_DIR),
            record_video_size={"width": 1440, "height": 900},
            locale="zh-CN",
        )
        context.add_init_script(CAPTION_JS)
        page = context.new_page()

        # ① 登录
        page.goto(f"{BASE_URL}/login", wait_until="networkidle")
        page.evaluate("window.__cap && window.__cap('① 登录：demo_user（虚构演示账号；JWT 短期令牌，前端只持有）')")
        pause(page, 6)
        type_like(page, "input[placeholder='请输入用户名']", "demo_user")
        type_like(page, "input[placeholder='请输入密码']", "demo-user-2026")
        pause(page, 2)
        page.click(".login__submit")
        page.wait_for_selector(".chat__suggestion", timeout=20000)

        # ② 政策问答（流式 token + 可定位引用）
        page.evaluate("window.__cap('② 政策问答：DeepSeek 流式回答，引用必须来自本次 pgvector 检索命中的官方政策片段；知识库外问题按规则拒答')")
        pause(page, 2)
        page.click(".chat__suggestion >> nth=0")
        page.wait_for_selector(".inline-cites__item", timeout=60000)
        pause(page, 14)  # 留足流式输出与阅读时间
        page.wait_for_timeout(3000)
        pause(page, 6)

        # ②c 第二轮政策问答：请求只携带上一组 user/assistant 上下文（不可信文本，仅进模型提示）
        page.evaluate("window.__cap('②c 多轮：客户端可带“上一组问答”作为上下文，但只是不可信文本——不能触发工具，也不能改变授权；本轮回答会参考上文')")
        type_like(page, ".chat__inputbar textarea", "那它和适老化改造分别面向哪些老人？", per_char=0.09)
        page.click("button:has-text('发送')")
        pause(page, 16)
        page.wait_for_timeout(2000)
        pause(page, 6)

        # ②b 知识库外问题：地域守卫拒答，不消耗模型额度
        page.evaluate("window.__cap('②b 边界：知识库外（上海政策）命中地域守卫直接拒答——宁可不说，不可编造；医疗剂量与绕过确认类问题同理')")
        type_like(page, ".chat__inputbar textarea", "上海市高龄津贴每月发放多少钱？", per_char=0.08)
        page.click("button:has-text('发送')")
        pause(page, 6)
        page.wait_for_selector("text=没有足够依据", timeout=30000)
        pause(page, 6)

        # ③ 服务卡片 + 预约草案
        page.evaluate("window.__cap('③ 服务查询：Python 经内部 Token 调用 Java 只读工具，返回数据库权威价格与有余量时段；含“预约”意图时 Java 生成短期 PENDING 草案（模型只能出草案）')")
        type_like(page, ".chat__inputbar textarea", "我想预约武侯区的助洁服务")
        page.click("button:has-text('发送')")
        page.wait_for_selector(".draft button:has-text('确认预约')", timeout=60000)
        pause(page, 10)
        page.mouse.wheel(0, -300)
        pause(page, 1)
        page.mouse.wheel(0, 300)
        pause(page, 8)

        # ④ 确认下单：只提交 draftId + Idempotency-Key
        page.evaluate("window.__cap('④ 用户确认：前端只提交 draftId + Idempotency-Key；Java 事务内原子消费草案、按数据库现价下单、条件扣减容量——模型与浏览器都不能创建最终预约')")
        page.click(".draft button:has-text('确认预约')")
        page.wait_for_selector("text=预约已确认", timeout=30000)
        pause(page, 11)
        page.click("button:has-text('查看我的预约')")

        # ⑤ 我的预约与取消回补
        page.wait_for_selector(".appointment", timeout=15000)
        pause(page, 6)
        page.evaluate("window.__cap('⑤ 我的预约：取消仅允许本人 CONFIRMED → CANCELLED 一次，容量条件更新只回补一次；重复取消返回现有状态')")
        pause(page, 5)
        page.click(".appointment button:has-text('取消预约')")
        page.wait_for_selector(".appointment__status.cancelled", timeout=30000)
        pause(page, 9)
        page.evaluate("window.__cap('⑤ 验证点：容量回补只发生一次（周 3 事务 + 周 6 Java 10/10 回归），前端按钮禁用不是安全保障')")
        pause(page, 5)

        # ⑥ 周 6 证据摘要
        page.evaluate("window.__cap('⑥ 周 6 证据收口：50 题评测、4 例注入自检、20 路 SSE 断连、并发防超卖、EXPLAIN ANALYZE、CI 全部门禁见仓库文件与 progress.md')")
        page.goto(SUMMARY_PAGE.as_uri())
        pause(page, 9)
        for _ in range(10):
            page.mouse.wheel(0, 240)
            pause(page, 2.8)
        pause(page, 6)
        page.evaluate("window.__cap('完 · CareAgent：唯一 MVP 路径 + 可复现证据（命令与原始输出见 progress.md 与 data/evaluation/）。所有账号与数据均为虚构演示数据。')")
        pause(page, 10)

        context.close()
        browser.close()

    videos = sorted(VIDEO_DIR.glob("*.webm"), key=lambda p: p.stat().st_mtime)
    if not videos:
        raise SystemExit("未生成录屏文件")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(videos[-1]), str(OUTPUT))
    return OUTPUT


def main() -> None:
    started = time.perf_counter()
    build_summary_page()
    output = record()
    duration = time.perf_counter() - started
    print(f"video: {output} (session {duration:.0f}s)")
    if duration < 165:
        print("WARNING: 录屏时长低于 3 分钟，请检查步骤等待是否需要加长")


if __name__ == "__main__":
    main()
