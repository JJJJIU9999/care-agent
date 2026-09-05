# 数据模型与状态机

## 通用规则

- 主键统一使用 UUID。
- 业务时间使用 PostgreSQL `TIMESTAMPTZ`。
- API 时间包含 `+08:00`，界面按 `Asia/Shanghai` 展示。
- 金额使用 `NUMERIC(10,2)`，禁止浮点数。
- 所有表包含 `created_at`；需要更新的表包含 `updated_at`。
- Java 只迁移 `app` schema，Python 只维护 `rag` schema。

## app schema

### app_user

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| username | VARCHAR(64) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| role | VARCHAR(16) | `USER` 或 `ADMIN` |
| enabled | BOOLEAN | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |

只保存演示账号，不保存真实个人资料。

### service_item

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| name | VARCHAR(100) | NOT NULL |
| category | VARCHAR(32) | NOT NULL |
| district | VARCHAR(32) | NOT NULL |
| description | TEXT | NOT NULL |
| price | NUMERIC(10,2) | NOT NULL, `price >= 0` |
| enabled | BOOLEAN | NOT NULL |

只通过种子数据创建，不提供管理 CRUD。

### service_slot

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| service_id | UUID | FK service_item |
| start_at | TIMESTAMPTZ | NOT NULL |
| end_at | TIMESTAMPTZ | NOT NULL |
| total_capacity | INTEGER | `> 0` |
| remaining_capacity | INTEGER | `>= 0` |

约束：

```sql
CHECK (end_at > start_at)
CHECK (remaining_capacity >= 0)
CHECK (remaining_capacity <= total_capacity)
```

索引：`(service_id, start_at)` 和 `(start_at, remaining_capacity)`。

### appointment_draft

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | FK app_user |
| service_id | UUID | FK service_item |
| slot_id | UUID | FK service_slot |
| status | VARCHAR(16) | `PENDING` 或 `USED` |
| expires_at | TIMESTAMPTZ | NOT NULL |
| used_at | TIMESTAMPTZ | NULL |

草案不保存模型生成的价格。展示和确认时均从 `service_item` 读取当前价格。

原子消费：

```sql
UPDATE app.appointment_draft
SET status = 'USED', used_at = now()
WHERE id = :draftId
  AND user_id = :userId
  AND status = 'PENDING'
  AND expires_at > now();
```

受影响行数必须为 1，否则事务失败。

### appointment

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| draft_id | UUID | UNIQUE, FK appointment_draft |
| user_id | UUID | FK app_user |
| service_id | UUID | FK service_item |
| slot_id | UUID | FK service_slot |
| confirmed_price | NUMERIC(10,2) | NOT NULL |
| status | VARCHAR(16) | `CONFIRMED` 或 `CANCELLED` |
| idempotency_key | VARCHAR(128) | NOT NULL |
| confirmed_at | TIMESTAMPTZ | NOT NULL |
| cancelled_at | TIMESTAMPTZ | NULL |

唯一约束：

- `UNIQUE(draft_id)`：同一草案只能形成一个预约。
- `UNIQUE(user_id, idempotency_key)`：同一用户的确认重试返回同一结果。

### conversation

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| user_id | UUID | FK app_user |
| created_at | TIMESTAMPTZ | NOT NULL |
| last_active_at | TIMESTAMPTZ | NOT NULL |

### message_metadata

| 字段 | 类型 | 约束 |
|---|---|---|
| id | UUID | PK |
| conversation_id | UUID | FK conversation |
| role | VARCHAR(16) | USER 或 ASSISTANT |
| char_count | INTEGER | NOT NULL |
| token_count | INTEGER | NULL |
| model | VARCHAR(100) | NULL |
| duration_ms | BIGINT | NULL |
| status | VARCHAR(16) | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |

不保存消息正文。

### critical_audit_event

只允许三种事件：

- `LOGIN_SUCCESS` / `LOGIN_FAILURE`
- `CREATE_APPOINTMENT`
- `CANCEL_APPOINTMENT`

保存事件类型、用户、Request ID、结果、目标资源 ID 和时间，不做通用审计框架。

## 预约确认事务

1. 按 `(user_id, idempotency_key)` 查询已有预约；存在则直接返回。
2. 原子消费属于当前用户且未过期的 `PENDING` 草案。
3. 重新读取启用中的服务和对应时段。
4. 执行容量原子扣减：

```sql
UPDATE app.service_slot
SET remaining_capacity = remaining_capacity - 1
WHERE id = :slotId
  AND service_id = :serviceId
  AND remaining_capacity > 0;
```

5. 受影响行数不是 1 时回滚。
6. 使用数据库当前价格插入预约。
7. 写入关键审计事件。
8. 提交事务。

任一步失败都必须回滚草案状态和容量变化。

## 取消事务

```sql
UPDATE app.appointment
SET status = 'CANCELLED', cancelled_at = now()
WHERE id = :appointmentId
  AND user_id = :userId
  AND status = 'CONFIRMED';
```

只有更新行数为 1 时才执行：

```sql
UPDATE app.service_slot
SET remaining_capacity = remaining_capacity + 1
WHERE id = :slotId
  AND remaining_capacity < total_capacity;
```

两条更新和取消审计记录位于同一事务。重复取消返回已有 `CANCELLED` 状态，不回补第二次。

## rag schema

### knowledge_document

保存标题、机构、生效日期、来源 URL、SHA-256、文件类型、处理方式、状态和失败原因。

### document_chunk

保存文档 ID、章节、页码、序号、正文、Embedding 和检索元数据。周 0 已选定 `BAAI/bge-small-zh-v1.5`，MVP 的 pgvector 向量维度固定为 512。

### ingestion_job

状态：`PENDING → PROCESSING → COMPLETED | FAILED`。失败必须记录可理解原因，不无限自动重试。

### evaluation_question

保存问题、`IN_SCOPE`/`OUT_OF_SCOPE`、预期文档、预期片段和预期行为。

### evaluation_run / evaluation_result

保存运行配置、模型、数据集版本、Hit@5、MRR、引用判断、拒答判断、Token 和各阶段耗时。

MVP 使用 PostgreSQL 初始化 SQL；Alembic 迁移列入 Roadmap。
