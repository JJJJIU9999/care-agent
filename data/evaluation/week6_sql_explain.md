# 周 6 SQL 执行计划证据（EXPLAIN ANALYZE）

- 日期：2026-09-06（UTC）
- 环境：Docker Desktop（Apple Silicon），容器 `careagent-postgres`（pgvector/pgvector:pg16，PostgreSQL 16.15，aarch64 Linux），单实例默认共享内存配置
- 复现命令：`docker exec -i careagent-postgres sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -q' < <本文件附录脚本>`
- 合成规模数据仅在一次性 `perf_lab` schema（由 `pg_dump -n app --schema-only` 克隆 + `generate_series` 虚构数据，无真实个人信息；取证后已 `DROP SCHEMA perf_lab CASCADE`）。
- 优化前后差异通过“同一事务内临时 DROP 索引 → EXPLAIN → ROLLBACK”获得，生产 `app` schema 的索引与迁移未做任何改动。

## 数据量

| 表 | perf_lab 合成量 | 真实 app schema 演示量 |
|---|---:|---:|
| service_slot | 200,000（200 服务 × 50 天 × 20 时段） | 3 |
| appointment | 100,000（200 用户 × 500 条） | 2 |
| app_user | 200 | 2 |
| service_item | 200 | 2 |

## 生产索引（V1 迁移，本次未修改）

- `service_slot_service_start_idx (service_id, start_at)`
- `service_slot_start_capacity_idx (start_at, remaining_capacity)`
- `appointment`：`UNIQUE (user_id, idempotency_key)` 约束索引、`appointment_user_start_idx (user_id, confirmed_at DESC)`、`draft_id UNIQUE`

## 1. 服务时段查询（ServiceSlotRepository.findAvailableFrom 等价 SQL，6 服务 + 起始时间，命中约 3,960/200,000 行）

- 优化后（现有索引）：`Bitmap Index Scan on service_slot_service_start_idx` + quicksort，**Execution Time 5.924 ms**（Rows Removed by Filter 390）
- 优化前（临时删除 service_start 索引）：回退 `Parallel Seq Scan`（Rows Removed by Filter 98,020），**Execution Time 9.851 ms**
- 无任何二级索引基线：**7.912 ms**（Gather 并行扫描，2 个并行 worker）
- 结论：`(service_id, start_at)` 组合索引把命中行从“全表并行扫描 + 过滤 98%”降为位图索引取 ~4.3k 行；本机 200k 行量级收益约 1.7×，量级越大差距越大。

## 2. 我的预约（AppointmentRepository.findOwnedOrderByStartAtDesc 等价 SQL）

```sql
SELECT a.* FROM appointment a, service_slot s
WHERE a.user_id = $1 AND a.slot_id = s.id ORDER BY s.start_at DESC;
```

- 优化后：**3.430 ms** — `Index Scan using appointment_user_start_idx`（user_id 前缀，500 行）→ `Nested Loop` + `service_slot_pkey` 回查 → 小排序（quicksort 110kB）
- 优化前（删除 appointment 两个 user_id 相关索引）：**17.460 ms** — `Parallel Seq Scan`（每 worker 过滤 ~49,750 行）+ Gather Merge
- 差异约 **5.1×**。排序键是 `slot.start_at`，`(user_id, confirmed_at DESC)` 索引不能消除排序，但 user_id 前缀把候选从 100k 压到 500，排序成本可忽略。
- 真实 app schema（2 条预约）：**0.034 ms**，计划形态与合成环境一致（同两个 Index Scan）。

## 3. 幂等查询（findByUserIdAndIdempotencyKey，每次确认请求的第一道查重）

```sql
SELECT a.* FROM appointment a WHERE a.user_id = $1 AND a.idempotency_key = $2;
```

- 优化后（`UNIQUE (user_id, idempotency_key)`）：**0.019 ms**，单行 Index Scan
- 仅有 `appointment_user_start_idx` 时：**0.036 ms**（user_id 索引扫描 + 过滤 499 行）
- 无任何可用索引：**4.782 ms**（`Seq Scan`，过滤 99,999 行）
- 唯一约束索引相对全表扫描收益约 **250×**；且该唯一约束同时是“同一用户同一幂等键至多一单”的正确性保障，删除索引不只影响性能。
- 真实 app schema（2 条预约）：**0.018 ms**（Index Only Scan 取键 + 主查询 Index Scan）。

## 汇总

| 查询 | 无索引基线 | 现有索引 | 差异 | 计划要点 |
|---|---:|---:|---:|---|
| 服务时段查询（200k 行） | 9.85 ms | 5.92 ms | ~1.7× | Bitmap Index Scan (service_id,start_at) |
| 我的预约（100k 行） | 17.46 ms | 3.43 ms | ~5.1× | Index Scan (user_id 前缀) + PK 回查 + 500 行小排序 |
| 幂等查询（100k 行） | 4.78 ms | 0.019 ms | ~250× | UNIQUE(user_id,idempotency_key) 单行命中 |

口径：以上为本机 Docker 单实例、合成虚构数据的 `EXPLAIN ANALYZE` 记录，属于“执行计划与索引有效性”证据，不构成生产吞吐承诺。真实演示库数据量极小（≤3 行），三条查询均 <0.05 ms，只用于确认索引在真实 schema 中被选择。

## 附录：取证脚本（原样保存于运行记录）

```sql
\echo '### Q1-A 服务时段查询（200k 行 service_slot，有 service_slot_service_start_idx(service_id,start_at)）'
EXPLAIN (ANALYZE, BUFFERS) SELECT s.* FROM perf_lab.service_slot s
WHERE s.service_id IN ('9f886dcd-0dcf-49d7-b1f4-13b80df7af3b','bbd6f577-2f65-44a3-a71f-bbfa37448554','69ba7f05-e46f-45d4-83d3-9d0e5efe97c8','236da7db-20f6-4f93-b6fa-0456394b24e8','365c8430-3979-4e19-9a79-7d29ff53d17b','1f8a5914-3d3f-4be2-9c60-ca5557b18700') AND s.start_at >= timestamptz '2026-06-01T00:00:00Z' AND s.remaining_capacity > 0
ORDER BY s.start_at;
\echo '### Q1-B 服务时段查询·优化前（同事务临时 DROP 该索引后 ROLLBACK）'
BEGIN;
DROP INDEX perf_lab.service_slot_service_start_idx;
EXPLAIN (ANALYZE, BUFFERS) SELECT s.* FROM perf_lab.service_slot s
WHERE s.service_id IN ('9f886dcd-0dcf-49d7-b1f4-13b80df7af3b','bbd6f577-2f65-44a3-a71f-bbfa37448554','69ba7f05-e46f-45d4-83d3-9d0e5efe97c8','236da7db-20f6-4f93-b6fa-0456394b24e8','365c8430-3979-4e19-9a79-7d29ff53d17b','1f8a5914-3d3f-4be2-9c60-ca5557b18700') AND s.start_at >= timestamptz '2026-06-01T00:00:00Z' AND s.remaining_capacity > 0
ORDER BY s.start_at;
ROLLBACK;
\echo '### Q2-A 我的预约（100k 行 appointment，每用户 500 条，有 appointment_user_start_idx(user_id,confirmed_at DESC)）'
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM perf_lab.appointment a, perf_lab.service_slot s
WHERE a.user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid AND a.slot_id = s.id
ORDER BY s.start_at DESC;
\echo '### Q2-B 我的预约·优化前（事务内先 DROP 约束索引再 DROP 组合索引后 ROLLBACK）'
BEGIN;
ALTER TABLE perf_lab.appointment DROP CONSTRAINT appointment_user_id_idempotency_key_key;
DROP INDEX perf_lab.appointment_user_start_idx;
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM perf_lab.appointment a, perf_lab.service_slot s
WHERE a.user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid AND a.slot_id = s.id
ORDER BY s.start_at DESC;
ROLLBACK;
\echo '### Q3-A 幂等查询（有 UNIQUE(user_id,idempotency_key) 约束索引）'
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM perf_lab.appointment a
WHERE a.user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid AND a.idempotency_key = 'key-1-1';
\echo '### Q3-B 幂等查询·优化前（事务内 DROP CONSTRAINT 后 ROLLBACK）'
BEGIN;
ALTER TABLE perf_lab.appointment DROP CONSTRAINT appointment_user_id_idempotency_key_key;
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM perf_lab.appointment a
WHERE a.user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid AND a.idempotency_key = 'key-1-1';
ROLLBACK;
\echo '### 真实 app schema（演示数据量级：3 slot / 2 appointment）三条查询'
EXPLAIN (ANALYZE, BUFFERS) SELECT s.* FROM app.service_slot s
WHERE s.service_id IN (SELECT id FROM app.service_item WHERE enabled) AND s.start_at >= timestamptz '2020-01-01T00:00:00Z' AND s.remaining_capacity > 0
ORDER BY s.start_at;
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM app.appointment a, app.service_slot s
WHERE a.user_id = '00000000-0000-0000-0000-000000000001'::uuid AND a.slot_id = s.id
ORDER BY s.start_at DESC;
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM app.appointment a
WHERE a.user_id = '00000000-0000-0000-0000-000000000001'::uuid
  AND a.idempotency_key = (SELECT idempotency_key FROM app.appointment WHERE user_id='00000000-0000-0000-0000-000000000001'::uuid ORDER BY idempotency_key LIMIT 1);
```

### 第二轮修正（Q1 参数改为区间内日期、Q3 无索引基线、Q1-B/C）
```sql
\echo '### Q1-A 服务时段查询（200k 行，有 service_slot_service_start_idx(service_id,start_at) + service_slot_start_capacity_idx(start_at,remaining_capacity)）'
EXPLAIN (ANALYZE, BUFFERS) SELECT s.* FROM perf_lab.service_slot s
WHERE s.service_id IN ('9f886dcd-0dcf-49d7-b1f4-13b80df7af3b','bbd6f577-2f65-44a3-a71f-bbfa37448554','69ba7f05-e46f-45d4-83d3-9d0e5efe97c8','236da7db-20f6-4f93-b6fa-0456394b24e8','365c8430-3979-4e19-9a79-7d29ff53d17b','1f8a5914-3d3f-4be2-9c60-ca5557b18700') AND s.start_at >= timestamptz '2026-01-15T00:00:00Z' AND s.remaining_capacity > 0
ORDER BY s.start_at;
\echo '### Q1-B 服务时段查询·仅保留 (start_at,remaining_capacity) 单索引（临时 DROP service_start 后 ROLLBACK）'
BEGIN;
DROP INDEX perf_lab.service_slot_service_start_idx;
EXPLAIN (ANALYZE, BUFFERS) SELECT s.* FROM perf_lab.service_slot s
WHERE s.service_id IN ('9f886dcd-0dcf-49d7-b1f4-13b80df7af3b','bbd6f577-2f65-44a3-a71f-bbfa37448554','69ba7f05-e46f-45d4-83d3-9d0e5efe97c8','236da7db-20f6-4f93-b6fa-0456394b24e8','365c8430-3979-4e19-9a79-7d29ff53d17b','1f8a5914-3d3f-4be2-9c60-ca5557b18700') AND s.start_at >= timestamptz '2026-01-15T00:00:00Z' AND s.remaining_capacity > 0
ORDER BY s.start_at;
ROLLBACK;
\echo '### Q1-C 服务时段查询·无任何二级索引（优化前基线，临时 DROP 两索引后 ROLLBACK）'
BEGIN;
DROP INDEX perf_lab.service_slot_service_start_idx;
DROP INDEX perf_lab.service_slot_start_capacity_idx;
EXPLAIN (ANALYZE, BUFFERS) SELECT s.* FROM perf_lab.service_slot s
WHERE s.service_id IN ('9f886dcd-0dcf-49d7-b1f4-13b80df7af3b','bbd6f577-2f65-44a3-a71f-bbfa37448554','69ba7f05-e46f-45d4-83d3-9d0e5efe97c8','236da7db-20f6-4f93-b6fa-0456394b24e8','365c8430-3979-4e19-9a79-7d29ff53d17b','1f8a5914-3d3f-4be2-9c60-ca5557b18700') AND s.start_at >= timestamptz '2026-01-15T00:00:00Z' AND s.remaining_capacity > 0
ORDER BY s.start_at;
ROLLBACK;
\echo '### Q3-B2 幂等查询·无任何可用索引（临时 DROP 唯一约束+组合索引后 ROLLBACK）'
BEGIN;
ALTER TABLE perf_lab.appointment DROP CONSTRAINT appointment_user_id_idempotency_key_key;
DROP INDEX perf_lab.appointment_user_start_idx;
EXPLAIN (ANALYZE, BUFFERS) SELECT a.* FROM perf_lab.appointment a
WHERE a.user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid AND a.idempotency_key = 'key-1-1';
ROLLBACK;
```

### 原始输出 1
```text
### Q1-A 服务时段查询（200k 行 service_slot，有 service_slot_service_start_idx(service_id,start_at)）
                                                                                                                                QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Index Scan using service_slot_start_capacity_idx on service_slot s  (cost=0.29..8.31 rows=1 width=72) (actual time=0.003..0.003 rows=0 loops=1)
   Index Cond: ((start_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (remaining_capacity > 0))
   Filter: (service_id = ANY ('{9f886dcd-0dcf-49d7-b1f4-13b80df7af3b,bbd6f577-2f65-44a3-a71f-bbfa37448554,69ba7f05-e46f-45d4-83d3-9d0e5efe97c8,236da7db-20f6-4f93-b6fa-0456394b24e8,365c8430-3979-4e19-9a79-7d29ff53d17b,1f8a5914-3d3f-4be2-9c60-ca5557b18700}'::uuid[]))
   Buffers: shared hit=2
 Planning:
   Buffers: shared hit=214 read=1
 Planning Time: 0.376 ms
 Execution Time: 0.010 ms
(8 rows)

### Q1-B 服务时段查询·优化前（同事务临时 DROP 该索引后 ROLLBACK）
                                                                                                                                QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Index Scan using service_slot_start_capacity_idx on service_slot s  (cost=0.29..8.31 rows=1 width=72) (actual time=0.001..0.001 rows=0 loops=1)
   Index Cond: ((start_at >= '2026-06-01 00:00:00+00'::timestamp with time zone) AND (remaining_capacity > 0))
   Filter: (service_id = ANY ('{9f886dcd-0dcf-49d7-b1f4-13b80df7af3b,bbd6f577-2f65-44a3-a71f-bbfa37448554,69ba7f05-e46f-45d4-83d3-9d0e5efe97c8,236da7db-20f6-4f93-b6fa-0456394b24e8,365c8430-3979-4e19-9a79-7d29ff53d17b,1f8a5914-3d3f-4be2-9c60-ca5557b18700}'::uuid[]))
   Buffers: shared hit=2
 Planning:
   Buffers: shared hit=7
 Planning Time: 0.042 ms
 Execution Time: 0.005 ms
(8 rows)

### Q2-A 我的预约（100k 行 appointment，每用户 500 条，有 appointment_user_start_idx(user_id,confirmed_at DESC)）
                                                                          QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=4651.82..4653.07 rows=499 width=146) (actual time=3.371..3.383 rows=500 loops=1)
   Sort Key: s.start_at DESC
   Sort Method: quicksort  Memory: 110kB
   Buffers: shared hit=2018
   ->  Nested Loop  (cost=0.71..4629.46 rows=499 width=146) (actual time=0.064..3.252 rows=500 loops=1)
         Buffers: shared hit=2015
         ->  Index Scan using appointment_user_start_idx on appointment a  (cost=0.29..807.15 rows=499 width=138) (actual time=0.042..0.151 rows=500 loops=1)
               Index Cond: (user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid)
               Buffers: shared hit=15
         ->  Index Scan using service_slot_pkey on service_slot s  (cost=0.42..7.66 rows=1 width=24) (actual time=0.006..0.006 rows=1 loops=500)
               Index Cond: (id = a.slot_id)
               Buffers: shared hit=2000
 Planning:
   Buffers: shared hit=196
 Planning Time: 0.342 ms
 Execution Time: 3.430 ms
(16 rows)

### Q2-B 我的预约·优化前（事务内先 DROP 约束索引再 DROP 组合索引后 ROLLBACK）
                                                                      QUERY PLAN
-------------------------------------------------------------------------------------------------------------------------------------------------------
 Gather Merge  (cost=6126.38..6160.19 rows=294 width=146) (actual time=16.304..17.413 rows=500 loops=1)
   Workers Planned: 1
   Workers Launched: 1
   Buffers: shared hit=4135
   ->  Sort  (cost=5126.37..5127.11 rows=294 width=146) (actual time=15.442..15.449 rows=250 loops=2)
         Sort Key: s.start_at DESC
         Sort Method: quicksort  Memory: 110kB
         Buffers: shared hit=4135
         Worker 0:  Sort Method: quicksort  Memory: 25kB
         ->  Nested Loop  (cost=0.42..5114.32 rows=294 width=146) (actual time=7.384..15.389 rows=250 loops=2)
               Buffers: shared hit=4127
               ->  Parallel Seq Scan on appointment a  (cost=0.00..2862.29 rows=294 width=138) (actual time=7.381..15.185 rows=250 loops=2)
                     Filter: (user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid)
                     Rows Removed by Filter: 49750
                     Buffers: shared hit=2127
               ->  Index Scan using service_slot_pkey on service_slot s  (cost=0.42..7.66 rows=1 width=24) (actual time=0.001..0.001 rows=1 loops=500)
                     Index Cond: (id = a.slot_id)
                     Buffers: shared hit=2000
 Planning:
   Buffers: shared hit=20
 Planning Time: 0.064 ms
 Execution Time: 17.460 ms
(22 rows)

### Q3-A 幂等查询（有 UNIQUE(user_id,idempotency_key) 约束索引）
                                                                       QUERY PLAN
---------------------------------------------------------------------------------------------------------------------------------------------------------
 Index Scan using appointment_user_id_idempotency_key_key on appointment a  (cost=0.42..8.44 rows=1 width=138) (actual time=0.014..0.014 rows=1 loops=1)
   Index Cond: ((user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid) AND ((idempotency_key)::text = 'key-1-1'::text))
   Buffers: shared hit=4
 Planning:
   Buffers: shared hit=34
 Planning Time: 0.076 ms
 Execution Time: 0.019 ms
(7 rows)

### Q3-B 幂等查询·优化前（事务内 DROP CONSTRAINT 后 ROLLBACK）
                                                                  QUERY PLAN
----------------------------------------------------------------------------------------------------------------------------------------------
 Index Scan using appointment_user_start_idx on appointment a  (cost=0.29..808.40 rows=1 width=138) (actual time=0.024..0.034 rows=1 loops=1)
   Index Cond: (user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid)
   Filter: ((idempotency_key)::text = 'key-1-1'::text)
   Rows Removed by Filter: 499
   Buffers: shared hit=15
 Planning:
   Buffers: shared hit=4
 Planning Time: 0.025 ms
 Execution Time: 0.036 ms
(9 rows)

### 真实 app schema（演示数据量级：3 slot / 2 appointment）三条查询
                                                                     QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=32.29..32.41 rows=45 width=72) (actual time=0.032..0.032 rows=3 loops=1)
   Sort Key: s.start_at
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=3
   ->  Hash Join  (cost=19.47..31.06 rows=45 width=72) (actual time=0.029..0.030 rows=3 loops=1)
         Hash Cond: (s.service_id = service_item.id)
         Buffers: shared hit=3
         ->  Bitmap Heap Scan on service_slot s  (cost=6.87..18.22 rows=90 width=72) (actual time=0.019..0.019 rows=3 loops=1)
               Recheck Cond: ((start_at >= '2020-01-01 00:00:00+00'::timestamp with time zone) AND (remaining_capacity > 0))
               Heap Blocks: exact=1
               Buffers: shared hit=2
               ->  Bitmap Index Scan on service_slot_start_capacity_idx  (cost=0.00..6.85 rows=90 width=0) (actual time=0.007..0.007 rows=7 loops=1)
                     Index Cond: ((start_at >= '2020-01-01 00:00:00+00'::timestamp with time zone) AND (remaining_capacity > 0))
                     Buffers: shared hit=1
         ->  Hash  (cost=11.60..11.60 rows=80 width=16) (actual time=0.008..0.008 rows=2 loops=1)
               Buckets: 1024  Batches: 1  Memory Usage: 9kB
               Buffers: shared hit=1
               ->  Seq Scan on service_item  (cost=0.00..11.60 rows=80 width=16) (actual time=0.007..0.007 rows=2 loops=1)
                     Filter: enabled
                     Buffers: shared hit=1
 Planning:
   Buffers: shared hit=101
 Planning Time: 0.123 ms
 Execution Time: 0.039 ms
(24 rows)

                                                                       QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=16.36..16.36 rows=1 width=460) (actual time=0.026..0.027 rows=2 loops=1)
   Sort Key: s.start_at DESC
   Sort Method: quicksort  Memory: 25kB
   Buffers: shared hit=6
   ->  Nested Loop  (cost=0.29..16.35 rows=1 width=460) (actual time=0.024..0.025 rows=2 loops=1)
         Buffers: shared hit=6
         ->  Index Scan using appointment_user_start_idx on appointment a  (cost=0.14..8.16 rows=1 width=452) (actual time=0.017..0.018 rows=2 loops=1)
               Index Cond: (user_id = '00000000-0000-0000-0000-000000000001'::uuid)
               Buffers: shared hit=2
         ->  Index Scan using service_slot_pkey on service_slot s  (cost=0.15..8.17 rows=1 width=24) (actual time=0.003..0.003 rows=1 loops=2)
               Index Cond: (id = a.slot_id)
               Buffers: shared hit=4
 Planning:
   Buffers: shared hit=85
 Planning Time: 0.151 ms
 Execution Time: 0.034 ms
(16 rows)

                                                                                QUERY PLAN
--------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Index Scan using appointment_user_start_idx on appointment a  (cost=8.31..16.33 rows=1 width=452) (actual time=0.014..0.015 rows=1 loops=1)
   Index Cond: (user_id = '00000000-0000-0000-0000-000000000001'::uuid)
   Filter: ((idempotency_key)::text = ($0)::text)
   Rows Removed by Filter: 1
   Buffers: shared hit=4
   InitPlan 1 (returns $0)
     ->  Limit  (cost=0.14..8.16 rows=1 width=274) (actual time=0.012..0.012 rows=1 loops=1)
           Buffers: shared hit=2
           ->  Index Only Scan using appointment_user_id_idempotency_key_key on appointment  (cost=0.14..8.16 rows=1 width=274) (actual time=0.012..0.012 rows=1 loops=1)
                 Index Cond: (user_id = '00000000-0000-0000-0000-000000000001'::uuid)
                 Heap Fetches: 1
                 Buffers: shared hit=2
 Planning:
   Buffers: shared hit=5
 Planning Time: 0.038 ms
 Execution Time: 0.018 ms
(16 rows)

```
### 原始输出 2
```text
### Q1-A 服务时段查询（200k 行，有 service_slot_service_start_idx(service_id,start_at) + service_slot_start_capacity_idx(start_at,remaining_capacity)）
                                                                                                                                                                           QUERY PLAN
-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Sort  (cost=3133.78..3143.63 rows=3941 width=72) (actual time=5.699..5.812 rows=3960 loops=1)
   Sort Key: start_at
   Sort Method: quicksort  Memory: 468kB
   Buffers: shared hit=1039
   ->  Bitmap Heap Scan on service_slot s  (cost=262.89..2898.42 rows=3941 width=72) (actual time=0.681..5.349 rows=3960 loops=1)
         Recheck Cond: ((service_id = ANY ('{9f886dcd-0dcf-49d7-b1f4-13b80df7af3b,bbd6f577-2f65-44a3-a71f-bbfa37448554,69ba7f05-e46f-45d4-83d3-9d0e5efe97c8,236da7db-20f6-4f93-b6fa-0456394b24e8,365c8430-3979-4e19-9a79-7d29ff53d17b,1f8a5914-3d3f-4be2-9c60-ca5557b18700}'::uuid[])) AND (start_at >= '2026-01-15 00:00:00+00'::timestamp with time zone))
         Filter: (remaining_capacity > 0)
         Rows Removed by Filter: 390
         Heap Blocks: exact=993
         Buffers: shared hit=1036
         ->  Bitmap Index Scan on service_slot_service_start_idx  (cost=0.00..261.90 rows=4341 width=0) (actual time=0.607..0.607 rows=4350 loops=1)
               Index Cond: ((service_id = ANY ('{9f886dcd-0dcf-49d7-b1f4-13b80df7af3b,bbd6f577-2f65-44a3-a71f-bbfa37448554,69ba7f05-e46f-45d4-83d3-9d0e5efe97c8,236da7db-20f6-4f93-b6fa-0456394b24e8,365c8430-3979-4e19-9a79-7d29ff53d17b,1f8a5914-3d3f-4be2-9c60-ca5557b18700}'::uuid[])) AND (start_at >= '2026-01-15 00:00:00+00'::timestamp with time zone))
               Buffers: shared hit=43
 Planning:
   Buffers: shared hit=212
 Planning Time: 0.263 ms
 Execution Time: 5.924 ms
(17 rows)

### Q1-B 服务时段查询·仅保留 (start_at,remaining_capacity) 单索引（临时 DROP service_start 后 ROLLBACK）
                                                                                                                                                                                        QUERY PLAN
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Gather Merge  (cost=6246.63..6513.20 rows=2318 width=72) (actual time=8.403..9.766 rows=3960 loops=1)
   Workers Planned: 1
   Workers Launched: 1
   Buffers: shared hit=2507
   ->  Sort  (cost=5246.62..5252.41 rows=2318 width=72) (actual time=7.556..7.612 rows=1980 loops=2)
         Sort Key: start_at
         Sort Method: quicksort  Memory: 307kB
         Buffers: shared hit=2507
         Worker 0:  Sort Method: quicksort  Memory: 210kB
         ->  Parallel Seq Scan on service_slot s  (cost=0.00..5117.06 rows=2318 width=72) (actual time=0.129..7.379 rows=1980 loops=2)
               Filter: ((start_at >= '2026-01-15 00:00:00+00'::timestamp with time zone) AND (remaining_capacity > 0) AND (service_id = ANY ('{9f886dcd-0dcf-49d7-b1f4-13b80df7af3b,bbd6f577-2f65-44a3-a71f-bbfa37448554,69ba7f05-e46f-45d4-83d3-9d0e5efe97c8,236da7db-20f6-4f93-b6fa-0456394b24e8,365c8430-3979-4e19-9a79-7d29ff53d17b,1f8a5914-3d3f-4be2-9c60-ca5557b18700}'::uuid[])))
               Rows Removed by Filter: 98020
               Buffers: shared hit=2470
 Planning:
   Buffers: shared hit=4
 Planning Time: 0.051 ms
 Execution Time: 9.851 ms
(17 rows)

### Q1-C 服务时段查询·无任何二级索引（优化前基线，临时 DROP 两索引后 ROLLBACK）
                                                                                                                                                                                        QUERY PLAN
------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
 Gather Merge  (cost=6246.63..6513.20 rows=2318 width=72) (actual time=6.670..7.819 rows=3960 loops=1)
   Workers Planned: 1
   Workers Launched: 1
   Buffers: shared hit=2507
   ->  Sort  (cost=5246.62..5252.41 rows=2318 width=72) (actual time=5.937..5.993 rows=1980 loops=2)
         Sort Key: start_at
         Sort Method: quicksort  Memory: 362kB
         Buffers: shared hit=2507
         Worker 0:  Sort Method: quicksort  Memory: 154kB
         ->  Parallel Seq Scan on service_slot s  (cost=0.00..5117.06 rows=2318 width=72) (actual time=0.057..5.779 rows=1980 loops=2)
               Filter: ((start_at >= '2026-01-15 00:00:00+00'::timestamp with time zone) AND (remaining_capacity > 0) AND (service_id = ANY ('{9f886dcd-0dcf-49d7-b1f4-13b80df7af3b,bbd6f577-2f65-44a3-a71f-bbfa37448554,69ba7f05-e46f-45d4-83d3-9d0e5efe97c8,236da7db-20f6-4f93-b6fa-0456394b24e8,365c8430-3979-4e19-9a79-7d29ff53d17b,1f8a5914-3d3f-4be2-9c60-ca5557b18700}'::uuid[])))
               Rows Removed by Filter: 98020
               Buffers: shared hit=2470
 Planning:
   Buffers: shared hit=4
 Planning Time: 0.061 ms
 Execution Time: 7.912 ms
(17 rows)

### Q3-B2 幂等查询·无任何可用索引（临时 DROP 唯一约束+组合索引后 ROLLBACK）
                                                      QUERY PLAN
----------------------------------------------------------------------------------------------------------------------
 Seq Scan on appointment a  (cost=0.00..3627.00 rows=1 width=138) (actual time=0.007..4.778 rows=1 loops=1)
   Filter: ((user_id = '03c89207-8896-4cf4-925b-01cac4bc2bfc'::uuid) AND ((idempotency_key)::text = 'key-1-1'::text))
   Rows Removed by Filter: 99999
   Buffers: shared hit=2127
 Planning:
   Buffers: shared hit=72
 Planning Time: 0.068 ms
 Execution Time: 4.782 ms
(8 rows)

```
