<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api/http'
import type { AppointmentView } from '@/api/types'
import { formatDateTime, formatPrice } from '@/utils/format'

const items = ref<AppointmentView[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await api.get<{ items: AppointmentView[] }>('/api/v1/appointments')
    items.value = res.items
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function cancel(item: AppointmentView) {
  try {
    await api.post<AppointmentView>(
      `/api/v1/appointments/${item.appointmentId}/cancel`,
      undefined,
      { 'Idempotency-Key': crypto.randomUUID() },
    )
    ElMessage.success('预约已取消，容量已回补')
    await load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '取消失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="appointments">
    <div class="appointments__head">
      <h1 class="font-display appointments__title">我的预约</h1>
      <el-button text type="primary" @click="load">刷新</el-button>
    </div>

    <el-empty v-if="!loading && !items.length" description="暂无预约，去咨询台生成一个吧" />

    <div v-else v-loading="loading" class="appointments__list">
      <div v-for="item in items" :key="item.appointmentId" class="appointment ca-card">
        <div class="appointment__main">
          <div class="appointment__name">{{ item.serviceName }}</div>
          <div class="appointment__time">
            {{ formatDateTime(item.startAt) }} – {{ formatDateTime(item.endAt).split(' ')[1] }}
          </div>
          <div class="appointment__price">{{ formatPrice(item.confirmedPrice) }}</div>
        </div>
        <div class="appointment__side">
          <span class="appointment__status" :class="item.status.toLowerCase()">
            {{ item.status === 'CONFIRMED' ? '已确认' : '已取消' }}
          </span>
          <el-button
            v-if="item.status === 'CONFIRMED'"
            size="small"
            @click="cancel(item)"
          >
            取消预约
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.appointments {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
  max-width: 760px;
  margin: 0 auto;
}
.appointments__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.appointments__title {
  font-size: 24px;
  margin: 0;
}
.appointments__list {
  min-height: 120px;
}
.appointment {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  margin-bottom: 12px;
}
.appointment__name {
  font-size: 16px;
  font-weight: 700;
}
.appointment__time {
  font-size: 14px;
  color: var(--ca-ink-soft);
  margin-top: 4px;
}
.appointment__price {
  font-size: 14px;
  color: var(--ca-accent-deep);
  margin-top: 4px;
  font-weight: 600;
}
.appointment__side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.appointment__status {
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
}
.appointment__status.confirmed {
  background: #e3f3ea;
  color: var(--ca-success);
}
.appointment__status.cancelled {
  background: #efece6;
  color: var(--ca-ink-faint);
}
</style>
