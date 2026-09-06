import { auth } from '@/stores/auth'
import type { LoginResponse } from './types'

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

interface ErrorBody {
  code?: string
  message?: string
}

async function parseBody(res: Response): Promise<unknown> {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Content-Type', 'application/json')
  if (auth.token) headers.set('Authorization', `Bearer ${auth.token}`)

  const res = await fetch(path, { ...init, headers })
  const body = await parseBody(res)

  if (res.status === 401 && !path.endsWith('/auth/login')) {
    auth.clear()
    window.location.assign('/login')
    throw new ApiError(401, 'UNAUTHORIZED', '登录已失效')
  }

  if (!res.ok) {
    const err = body as ErrorBody
    throw new ApiError(res.status, err?.code ?? 'ERROR', err?.message ?? `请求失败（${res.status}）`)
  }
  return body as T
}

export const api = {
  get<T>(path: string): Promise<T> {
    return request<T>(path)
  },
  post<T>(path: string, data?: unknown, extraHeaders?: Record<string, string>): Promise<T> {
    return request<T>(path, {
      method: 'POST',
      body: data === undefined ? undefined : JSON.stringify(data),
      headers: extraHeaders,
    })
  },
  async uploadForm<T>(path: string, form: FormData): Promise<T> {
    const headers = new Headers()
    if (auth.token) headers.set('Authorization', `Bearer ${auth.token}`)

    const res = await fetch(path, { method: 'POST', headers, body: form })
    const body = await parseBody(res)

    if (res.status === 401) {
      auth.clear()
      window.location.assign('/login')
      throw new ApiError(401, 'UNAUTHORIZED', '登录已失效')
    }
    if (!res.ok) {
      const err = body as ErrorBody
      throw new ApiError(res.status, err?.code ?? 'ERROR', err?.message ?? `请求失败（${res.status}）`)
    }
    return body as T
  },
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}
