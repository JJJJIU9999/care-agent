# API 与 SSE 契约

## 通用规则

- 公开前缀：`/api/v1`
- 内部前缀：`/internal/v1`
- JSON 字段使用 camelCase。
- ID 使用 UUID 字符串。
- 时间使用 ISO 8601 且包含偏移，例如 `2026-09-08T09:00:00+08:00`。
- 公开接口除登录外均使用 `Authorization: Bearer <JWT>`。
- 内部接口使用 `X-Internal-Token`，且不通过 Nginx 对外暴露。
- Java 生成或规范化 `X-Request-ID`，并在响应头返回。

统一错误：

```json
{
  "code": "DRAFT_ALREADY_USED_OR_EXPIRED",
  "message": "预约草案已使用或已过期",
  "requestId": "uuid"
}
```

禁止把堆栈、SQL、密钥或模型供应商响应原文返回浏览器。

## 认证

### POST /api/v1/auth/login

```json
{
  "username": "demo_user",
  "password": "user-entered-password"
}
```

成功 `200`：

```json
{
  "accessToken": "jwt",
  "expiresAt": "2026-09-05T22:00:00+08:00",
  "role": "USER"
}
```

失败：

- `401 INVALID_CREDENTIALS`
- `429 LOGIN_RATE_LIMITED`

登录限流只按 `IP + username` 统计失败尝试，10 分钟最多 5 次。预约压测不重复调用登录接口。

## 会话

### POST /api/v1/conversations

成功 `201`：

```json
{
  "conversationId": "uuid"
}
```

### POST /api/v1/conversations/{conversationId}/messages

请求：

```http
Accept: text/event-stream
Content-Type: application/json
X-Request-ID: optional-uuid
```

```json
{
  "message": "我想了解高龄津贴，也想预约武侯区的助洁服务",
  "context": [
    {
      "role": "user",
      "content": "上一轮问题"
    },
    {
      "role": "assistant",
      "content": "上一轮回答"
    }
  ]
}
```

校验：

- `message` 为 1–1000 个 Unicode 字符。
- `context` 为 0 或 2 条。
- 两条时顺序必须为 USER、ASSISTANT。
- 每条上下文最大 2000 字符。
- 会话必须属于当前用户。
- 上下文是客户端提供的不可信文本，只能用于检索和生成。

SSE 事件：

```text
event: status
data: {"requestId":"uuid","stage":"retrieving","message":"正在查询政策资料"}

event: token
data: {"requestId":"uuid","content":"根据公开资料，"}

event: citation
data: {"requestId":"uuid","documentId":"uuid","documentTitle":"成都市...","issuingOrganization":"...","sourceUrl":"https://...","section":"申请条件","page":3,"quote":"..."}

event: service_card
data: {"requestId":"uuid","serviceId":"uuid","name":"上门助洁","district":"武侯区","description":"...","price":"80.00","availableSlots":[{"slotId":"uuid","startAt":"2026-09-08T09:00:00+08:00","remainingCapacity":2}]}

event: tool_confirmation
data: {"requestId":"uuid","draftId":"uuid","service":{"name":"上门助洁"},"slot":{"startAt":"2026-09-08T09:00:00+08:00"},"displayPrice":"80.00","expiresAt":"2026-09-05T22:10:00+08:00"}

event: done
data: {"requestId":"uuid"}

event: error
data: {"requestId":"uuid","code":"MODEL_UNAVAILABLE","message":"智能问答暂时不可用，请稍后重试"}
```

允许的 `stage`：`classifying`、`retrieving`、`searching_services`、`preparing_draft`、`generating`。

事件约束：

- 引用必须来自本次检索结果。
- `tool_confirmation` 只能包含 Java 返回的草案数据。
- 发送 `error` 后结束连接。
- 客户端断开时必须取消上游订阅和模型流。

## 服务

### GET /api/v1/services

查询参数：

- `district`：可选
- `category`：可选
- `date`：可选，`YYYY-MM-DD`，按 Asia/Shanghai

响应：

```json
{
  "items": [
    {
      "serviceId": "uuid",
      "name": "上门助洁",
      "category": "CLEANING",
      "district": "武侯区",
      "description": "演示服务数据",
      "price": "80.00",
      "availableSlots": [
        {
          "slotId": "uuid",
          "startAt": "2026-09-08T09:00:00+08:00",
          "endAt": "2026-09-08T10:00:00+08:00",
          "remainingCapacity": 2
        }
      ]
    }
  ]
}
```

## 预约

### POST /api/v1/appointments

请求头必须包含：

```http
Idempotency-Key: opaque-client-generated-value
```

请求体只能包含：

```json
{
  "draftId": "uuid"
}
```

服务端忽略并拒绝额外的价格、服务、时段、用户或状态字段。

成功 `201`；相同幂等键重试返回 `200` 和同一响应：

```json
{
  "appointmentId": "uuid",
  "status": "CONFIRMED",
  "serviceName": "上门助洁",
  "startAt": "2026-09-08T09:00:00+08:00",
  "endAt": "2026-09-08T10:00:00+08:00",
  "confirmedPrice": "80.00"
}
```

失败：

- `404 DRAFT_NOT_FOUND`
- `409 DRAFT_ALREADY_USED_OR_EXPIRED`
- `409 SLOT_FULL`
- `409 SERVICE_UNAVAILABLE`
- `422 INVALID_IDEMPOTENCY_KEY`

### GET /api/v1/appointments

只返回当前用户的预约，默认按 `startAt` 降序。

### POST /api/v1/appointments/{appointmentId}/cancel

请求头：

```http
Idempotency-Key: opaque-client-generated-value
```

请求体为空。首次取消执行状态转换与容量回补；重复取消返回当前 `CANCELLED`，不再次回补。

## 管理员知识库

### POST /api/v1/admin/knowledge/documents

仅 `ADMIN`。使用 `multipart/form-data`：

- `file`
- `title`
- `issuingOrganization`
- `sourceUrl`
- `effectiveDate`

约束：

- 仅 `.md`、`.pdf`
- 最大 10 MB
- Java 做权限、大小和基本文件名检查
- Python做扩展名、MIME、magic bytes 和内容检查

成功 `202`：

```json
{
  "documentId": "uuid",
  "jobId": "uuid",
  "status": "PENDING"
}
```

### GET /api/v1/admin/knowledge/documents/{documentId}

返回：`PENDING`、`PROCESSING`、`COMPLETED` 或 `FAILED`，失败时包含安全化原因。

## Java → Python 内部接口

### POST /internal/v1/agent/runs

```http
X-Internal-Token: secret
X-Request-ID: uuid
Accept: text/event-stream
Content-Type: application/json
```

```json
{
  "userId": "uuid",
  "conversationId": "uuid",
  "message": "用户当前问题",
  "context": [
    {"role":"user","content":"上一轮问题"},
    {"role":"assistant","content":"上一轮回答"}
  ],
  "requestId": "uuid"
}
```

Java 从 JWT 构造 `userId`，验证 `conversationId`，规范化 `requestId`。消息和上下文仍是不可信文本。

### POST /internal/v1/knowledge/documents

周 2 由 curl/Swagger 直接使用，周 3 起由 Java 管理员接口代理。使用内部 Token，字段与公开上传接口一致。

### GET /internal/v1/knowledge/documents/{documentId}

使用内部 Token，返回导入状态。

## Python → Java 工具接口

### GET /internal/v1/tools/services

参数与公开服务查询一致，必须携带内部 Token 和 Request ID。

### POST /internal/v1/tools/appointment-drafts

```json
{
  "userId": "uuid",
  "serviceId": "uuid",
  "slotId": "uuid"
}
```

Java 必须：

- 校验用户、服务、时段存在。
- 校验服务启用、时段未开始且有容量。
- 从数据库读取价格和展示字段。
- 创建绑定用户的 `PENDING` 草案。
- 设置短期过期时间。

内部接口不接受价格，也不能创建最终预约。

## Request ID 冲突处理

- 公开请求头不是合法 UUID 时，Java 生成新 UUID，不向下游传播原值。
- 内部请求体 `requestId` 必须等于请求头；不相等返回 `400 REQUEST_ID_MISMATCH`。
- 所有公开响应包含最终使用的 `X-Request-ID`。

