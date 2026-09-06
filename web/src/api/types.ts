// CareAgent 前端类型：与 docs/03-api-contract.md 及 Java/Python 实际响应一致。

export type Role = 'USER' | 'ADMIN'

export interface LoginResponse {
  accessToken: string
  expiresAt: string
  role: Role
}

export interface ConversationResponse {
  conversationId: string
}

export interface ContextMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface SlotView {
  slotId: string
  startAt: string
  endAt: string
  remainingCapacity: number
}

export interface ServiceView {
  serviceId: string
  name: string
  category: string
  district: string
  description: string
  price: string
  availableSlots: SlotView[]
}

export interface ServicesResponse {
  items: ServiceView[]
}

export interface AppointmentView {
  appointmentId: string
  status: string
  serviceName: string
  startAt: string
  endAt: string
  confirmedPrice: number | string
}

export interface AppointmentList {
  items: AppointmentView[]
}

// SSE 事件：仅允许这七种，与 Java PythonAgentClient.ALLOWED_EVENTS 一致。
export type SseEventName =
  | 'status'
  | 'token'
  | 'citation'
  | 'service_card'
  | 'tool_confirmation'
  | 'done'
  | 'error'

export interface StatusEvent {
  requestId: string
  stage: string
  message: string
}

export interface TokenEvent {
  requestId: string
  content: string
}

export interface CitationEvent {
  requestId: string
  documentId: string
  documentTitle: string
  issuingOrganization: string
  sourceUrl: string
  section: string
  page: number | null
  quote: string
}

export interface ServiceCardEvent {
  requestId: string
  serviceId: string
  name: string
  category: string
  district: string
  description: string
  price: string
  availableSlots: SlotView[]
}

export interface ToolConfirmationEvent {
  requestId: string
  draftId: string
  service: { name: string }
  slot: { startAt: string; endAt: string }
  displayPrice: string
  expiresAt: string
}

export interface DoneEvent {
  requestId: string
}

export interface ErrorEvent {
  requestId: string
  code: string
  message: string
}
