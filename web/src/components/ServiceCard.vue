<script setup lang="ts">
import type { ServiceView } from '@/api/types'
import { formatDateTime, formatPrice } from '@/utils/format'

defineProps<{ service: ServiceView }>()

const CATEGORY_LABEL: Record<string, string> = {
  CLEANING: '助洁',
  MEAL_DELIVERY: '助餐',
}
</script>

<template>
  <div class="service-card ca-card">
    <div class="service-card__head">
      <div>
        <div class="service-card__name">{{ service.name }}</div>
        <div class="service-card__meta">
          <span class="service-card__district">{{ service.district }}</span>
          <span class="service-card__tag">{{ CATEGORY_LABEL[service.category] ?? service.category }}</span>
        </div>
      </div>
      <div class="service-card__price font-display">{{ formatPrice(service.price) }}</div>
    </div>

    <p class="service-card__desc">{{ service.description }}</p>

    <div v-if="service.availableSlots.length" class="service-card__slots">
      <div class="service-card__slots-title">可选时段</div>
      <div v-for="slot in service.availableSlots" :key="slot.slotId" class="service-card__slot">
        <span class="service-card__slot-time">
          {{ formatDateTime(slot.startAt) }} – {{ formatDateTime(slot.endAt).split(' ')[1] }}
        </span>
        <span class="service-card__slot-left">余 {{ slot.remainingCapacity }} 位</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.service-card {
  max-width: 460px;
  padding: 18px 20px;
  margin: 8px 0;
}
.service-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.service-card__name {
  font-size: 17px;
  font-weight: 700;
}
.service-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}
.service-card__district {
  font-size: 13px;
  color: var(--ca-ink-soft);
}
.service-card__tag {
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--ca-primary-light-9);
  color: var(--ca-primary-deep);
}
.service-card__price {
  font-size: 20px;
  color: var(--ca-accent-deep);
  white-space: nowrap;
}
.service-card__desc {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--ca-ink-soft);
}
.service-card__slots {
  margin-top: 12px;
}
.service-card__slots-title {
  font-size: 12px;
  color: var(--ca-ink-faint);
  margin-bottom: 6px;
}
.service-card__slot {
  display: flex;
  justify-content: space-between;
  padding: 7px 10px;
  margin-top: 6px;
  border: 1px solid var(--ca-line);
  border-radius: 8px;
  font-size: 13px;
  background: #fdfbf6;
}
.service-card__slot-left {
  color: var(--ca-success);
  font-weight: 500;
}
</style>
