import { ApiError } from './http'
import type { SseEventName } from './types'

export interface SseHandlers {
  onEvent: (name: SseEventName, data: unknown) => void
}

/**
 * 用 fetch 消费 POST 接口的 SSE 流。
 * 原生 EventSource 只支持 GET，而会话消息接口是 POST，因此手动解析
 * `event:` / `data:` 帧。Java 用 SseEmitter 原样转发 Python 的七种事件。
 */
export async function streamSse(
  path: string,
  body: unknown,
  token: string,
  handlers: SseHandlers,
  signal: AbortSignal,
): Promise<void> {
  const res = await fetch(path, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'X-Request-ID': crypto.randomUUID(),
    },
    body: JSON.stringify(body),
    signal,
  })

  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => '')
    let code = 'SSE_ERROR'
    let message = `请求失败（${res.status}）`
    try {
      const parsed = JSON.parse(text)
      code = parsed.code ?? code
      message = parsed.message ?? message
    } catch {
      /* keep defaults */
    }
    throw new ApiError(res.status, code, message)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let sep = buffer.indexOf('\n\n')
    while (sep !== -1) {
      const block = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const parsed = parseBlock(block)
      if (parsed) {
        handlers.onEvent(parsed.event, parsed.data)
        if (parsed.event === 'done' || parsed.event === 'error') return
      }
      sep = buffer.indexOf('\n\n')
    }
  }
}

function parseBlock(block: string): { event: SseEventName; data: unknown } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const rawLine of block.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return null

  const raw = dataLines.join('\n')
  let data: unknown = raw
  try {
    data = JSON.parse(raw)
  } catch {
    /* non-JSON data is still forwarded as text */
  }
  return { event: event as SseEventName, data }
}
