<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { api } from '@/api/http'
import { streamSse } from '@/api/sse'
import { auth } from '@/stores/auth'
import type {
  AppointmentView,
  CitationEvent,
  ContextMessage,
  ServiceCardEvent,
  SseEventName,
  ToolConfirmationEvent,
} from '@/api/types'
import CitationPanel from '@/components/CitationPanel.vue'
import ServiceCard from '@/components/ServiceCard.vue'
import { formatDateTime, formatPrice } from '@/utils/format'

interface UserTurn {
  id: string
  role: 'user'
  text: string
}
interface AssistantTurn {
  id: string
  role: 'assistant'
  text: string
  statusText: string
  streaming: boolean
  error: string | null
  citations: CitationEvent[]
  serviceCards: ServiceCardEvent[]
  draft: ToolConfirmationEvent | null
  draftState: 'idle' | 'confirming' | 'confirmed' | 'error'
}
type Turn = UserTurn | AssistantTurn

const router = useRouter()
const messages = ref<Turn[]>([])
const input = ref('')
const sending = ref(false)
const conversationId = ref<string | null>(null)
const listEl = ref<HTMLElement | null>(null)

let abortController: AbortController | null = null
let lastUserText = ''
let lastAssistantText = ''

const suggestions = [
  '四川省高龄津贴面向多少周岁以上老年人？',
  '家庭适老化改造面向哪类老年人？',
  '我想预约武侯区的助洁服务',
]

const latestCitations = computed<CitationEvent[]>(() => {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const turn = messages.value[i]
    if (turn.role === 'assistant') return turn.citations
  }
  return []
})

function scrollToBottom() {
  nextTick(() => {
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  })
}

async function ensureConversation(): Promise<string> {
  if (conversationId.value) return conversationId.value
  const res = await api.post<{ conversationId: string }>('/api/v1/conversations')
  conversationId.value = res.conversationId
  return res.conversationId
}

function contextForNext(): ContextMessage[] {
  if (lastUserText && lastAssistantText) {
    return [
      { role: 'user', content: lastUserText },
      { role: 'assistant', content: lastAssistantText },
    ]
  }
  return []
}

async function send(text?: string) {
  const question = (text ?? input.value).trim()
  if (!question || sending.value) return
  input.value = ''

  const assistant: AssistantTurn = {
    id: crypto.randomUUID(),
    role: 'assistant',
    text: '',
    statusText: '',
    streaming: true,
    error: null,
    citations: [],
    serviceCards: [],
    draft: null,
    draftState: 'idle',
  }
  messages.value.push({ id: crypto.randomUUID(), role: 'user', text: question })
  messages.value.push(assistant)
  scrollToBottom()

  sending.value = true
  const context = contextForNext()
  const currentAssistantText = () => assistant.text

  try {
    const convId = await ensureConversation()
    abortController = new AbortController()

    await streamSse(
      `/api/v1/conversations/${convId}/messages`,
      { message: question, context },
      auth.token,
      {
        onEvent(name: SseEventName, data: unknown) {
          handleEvent(name, data, assistant)
        },
      },
      abortController.signal,
    )

    lastUserText = question
    lastAssistantText = currentAssistantText()
  } catch (err) {
    if (!(err instanceof DOMException && err.name === 'AbortError')) {
      assistant.error = err instanceof Error ? err.message : '请求失败'
    }
  } finally {
    assistant.streaming = false
    assistant.statusText = ''
    sending.value = false
    abortController = null
    scrollToBottom()
  }
}

function handleEvent(name: SseEventName, data: unknown, assistant: AssistantTurn) {
  const payload = data as Record<string, unknown>
  switch (name) {
    case 'status':
      assistant.statusText = String(payload.message ?? '')
      break
    case 'token':
      assistant.text += String(payload.content ?? '')
      break
    case 'citation':
      assistant.citations.push(payload as unknown as CitationEvent)
      break
    case 'service_card':
      assistant.serviceCards.push(payload as unknown as ServiceCardEvent)
      break
    case 'tool_confirmation':
      assistant.draft = payload as unknown as ToolConfirmationEvent
      break
    case 'error':
      assistant.error = String(payload.message ?? '服务暂时不可用')
      break
    case 'done':
      break
  }
  scrollToBottom()
}

function stop() {
  abortController?.abort()
}

async function confirmDraft(turn: AssistantTurn) {
  if (!turn.draft || turn.draftState === 'confirming') return
  turn.draftState = 'confirming'
  try {
    await api.post<AppointmentView>(
      '/api/v1/appointments',
      { draftId: turn.draft.draftId },
      { 'Idempotency-Key': crypto.randomUUID() },
    )
    turn.draftState = 'confirmed'
    ElMessage.success('预约已确认，可在“我的预约”查看')
  } catch (err) {
    turn.draftState = 'error'
    ElMessage.error(err instanceof Error ? err.message : '确认失败')
  }
}

function goAppointments() {
  router.push({ name: 'appointments' })
}
</script>

<template>
  <div class="chat">
    <div class="chat__main">
      <div ref="listEl" class="chat__list">
        <div v-if="!messages.length" class="chat__empty">
          <h2 class="font-display chat__empty-title">您好，今天想了解什么？</h2>
          <p class="chat__empty-sub">我可以按政府公开政策回答养老问题，也能帮您查询并预约上门服务。</p>
          <div class="chat__suggestions">
            <button v-for="s in suggestions" :key="s" class="chat__suggestion" @click="send(s)">
              {{ s }}
            </button>
          </div>
        </div>

        <template v-for="turn in messages" :key="turn.id">
          <div v-if="turn.role === 'user'" class="row row--user">
            <div class="bubble bubble--user">{{ turn.text }}</div>
          </div>

          <div v-else class="row row--assistant">
            <div class="bubble bubble--assistant">
              <div v-if="turn.statusText && turn.streaming" class="status-line">
                <span class="status-dot" />{{ turn.statusText }}
              </div>

              <div v-if="turn.text" class="answer-text">{{ turn.text }}<span v-if="turn.streaming" class="cursor" /></div>

              <div v-if="turn.error" class="answer-error">{{ turn.error }}</div>

              <ServiceCard v-for="card in turn.serviceCards" :key="card.serviceId" :service="card" />

              <div v-if="turn.draft" class="draft ca-card">
                <div class="draft__title font-display">预约确认</div>
                <div class="draft__row">
                  <span class="draft__label">服务</span>
                  <span>{{ turn.draft.service.name }}</span>
                </div>
                <div class="draft__row">
                  <span class="draft__label">时间</span>
                  <span>
                    {{ formatDateTime(turn.draft.slot.startAt) }} –
                    {{ formatDateTime(turn.draft.slot.endAt).split(' ')[1] }}
                  </span>
                </div>
                <div class="draft__row">
                  <span class="draft__label">费用</span>
                  <span class="draft__price">{{ formatPrice(turn.draft.displayPrice) }}</span>
                </div>

                <div v-if="turn.draftState === 'confirmed'" class="draft__done">
                  ✓ 预约已确认
                  <el-button size="small" text type="primary" @click="goAppointments">查看我的预约</el-button>
                </div>
                <div v-else-if="turn.draftState === 'error'" class="draft__error">确认失败，请重试</div>
                <el-button v-else type="primary" :loading="turn.draftState === 'confirming'" @click="confirmDraft(turn)">
                  确认预约
                </el-button>
              </div>

              <div v-if="turn.citations.length" class="inline-cites">
                <div class="inline-cites__title">引用</div>
                <a
                  v-for="(c, i) in turn.citations"
                  :key="`${c.documentId}-${i}`"
                  class="inline-cites__item"
                  :href="c.sourceUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  [{{ i + 1 }}] {{ c.documentTitle }}
                </a>
              </div>
            </div>
          </div>
        </template>
      </div>

      <div class="chat__inputbar">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="1000"
          placeholder="输入问题，例如：四川省高龄津贴面向多少周岁以上老年人？"
          @keydown.enter.exact.prevent="send()"
        />
        <div class="chat__inputbar-actions">
          <span class="chat__counter">{{ input.length }}/1000</span>
          <el-button v-if="sending" @click="stop">停止</el-button>
          <el-button v-else type="primary" :disabled="!input.trim()" @click="send()">发送</el-button>
        </div>
      </div>
    </div>

    <aside class="chat__side">
      <CitationPanel :citations="latestCitations" />
    </aside>
  </div>
</template>

<style scoped>
.chat {
  display: flex;
  height: 100%;
}
.chat__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.chat__list {
  flex: 1;
  overflow-y: auto;
  padding: 28px 32px;
}
.chat__empty {
  max-width: 520px;
  margin: 12vh auto 0;
  text-align: center;
}
.chat__empty-title {
  font-size: 26px;
  margin: 0 0 10px;
}
.chat__empty-sub {
  color: var(--ca-ink-soft);
  margin: 0 0 26px;
}
.chat__suggestions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.chat__suggestion {
  text-align: left;
  padding: 13px 16px;
  border: 1px solid var(--ca-line);
  border-radius: 12px;
  background: var(--ca-surface);
  color: var(--ca-ink);
  font-size: 14.5px;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.chat__suggestion:hover {
  border-color: var(--ca-primary);
  box-shadow: 0 8px 20px -14px rgba(14, 122, 107, 0.5);
}

.row {
  display: flex;
  margin-bottom: 18px;
}
.row--user {
  justify-content: flex-end;
}
.bubble {
  max-width: 76%;
  padding: 12px 16px;
  border-radius: 16px;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble--user {
  background: var(--ca-primary);
  color: #fff;
  border-bottom-right-radius: 6px;
}
.bubble--assistant {
  background: var(--ca-surface);
  border: 1px solid var(--ca-line);
  border-bottom-left-radius: 6px;
  box-shadow: 0 6px 18px -14px rgba(41, 42, 38, 0.3);
}
.status-line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ca-ink-soft);
  margin-bottom: 6px;
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ca-accent);
  animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.35; transform: scale(0.9); }
  50% { opacity: 1; transform: scale(1); }
}
.answer-text {
  white-space: pre-wrap;
}
.cursor {
  display: inline-block;
  width: 8px;
  height: 1.1em;
  vertical-align: -2px;
  background: var(--ca-primary);
  animation: blink 0.9s step-end infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.answer-error {
  color: var(--ca-danger);
  font-size: 14px;
  margin-top: 6px;
}

.draft {
  margin-top: 14px;
  padding: 16px 18px;
  max-width: 420px;
  background: #fdfbf6;
}
.draft__title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 10px;
}
.draft__row {
  display: flex;
  gap: 14px;
  padding: 6px 0;
  font-size: 14px;
}
.draft__label {
  color: var(--ca-ink-soft);
  min-width: 44px;
}
.draft__price {
  color: var(--ca-accent-deep);
  font-weight: 600;
}
.draft__done {
  color: var(--ca-success);
  margin: 8px 0;
  font-size: 14px;
}
.draft__error {
  color: var(--ca-danger);
  margin: 8px 0;
  font-size: 13px;
}

.inline-cites {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed var(--ca-line);
}
.inline-cites__title {
  font-size: 12px;
  color: var(--ca-ink-faint);
  margin-bottom: 4px;
}
.inline-cites__item {
  display: block;
  font-size: 12.5px;
  color: var(--ca-primary-deep);
  text-decoration: none;
  padding: 2px 0;
}
.inline-cites__item:hover {
  text-decoration: underline;
}

.chat__inputbar {
  padding: 16px 32px 20px;
  background: rgba(255, 255, 255, 0.7);
  border-top: 1px solid var(--ca-line);
}
.chat__inputbar :deep(.el-textarea__inner) {
  border-radius: 14px;
  font-size: 15px;
}
.chat__inputbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}
.chat__counter {
  font-size: 12px;
  color: var(--ca-ink-faint);
}

.chat__side {
  width: 320px;
  flex: none;
  padding: 24px 20px;
  overflow-y: auto;
  background: rgba(255, 255, 255, 0.55);
  border-left: 1px solid var(--ca-line);
}

@media (max-width: 900px) {
  .chat__side {
    display: none;
  }
  .bubble {
    max-width: 92%;
  }
  .chat__list {
    padding: 18px 14px;
  }
  .chat__inputbar {
    padding: 12px 14px 16px;
  }
}
</style>
