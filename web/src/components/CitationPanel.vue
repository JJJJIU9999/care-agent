<script setup lang="ts">
import type { CitationEvent } from '@/api/types'
import { Document } from '@element-plus/icons-vue'

defineProps<{ citations: CitationEvent[] }>()
</script>

<template>
  <div class="citation-panel">
    <div class="citation-panel__title font-display">引用来源</div>
    <p v-if="!citations.length" class="citation-panel__empty">回答所用资料将在此显示</p>

    <div v-for="(c, i) in citations" :key="`${c.documentId}-${i}`" class="citation ca-card">
      <div class="citation__index">{{ i + 1 }}</div>
      <div class="citation__body">
        <a class="citation__title" :href="c.sourceUrl" target="_blank" rel="noopener noreferrer">
          {{ c.documentTitle }}
        </a>
        <div class="citation__org">{{ c.issuingOrganization }}</div>
        <div class="citation__loc">
          <span v-if="c.section">{{ c.section }}</span>
          <span v-if="c.page"> · 第 {{ c.page }} 页</span>
        </div>
        <blockquote class="citation__quote">{{ c.quote }}</blockquote>
      </div>
    </div>

    <div v-if="citations.length" class="citation-panel__hint">
      <el-icon><Document /></el-icon>
      <span>点击标题查看政府原文</span>
    </div>
  </div>
</template>

<style scoped>
.citation-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.citation-panel__title {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 12px;
  color: var(--ca-ink);
}
.citation-panel__empty {
  font-size: 13px;
  color: var(--ca-ink-faint);
}
.citation {
  display: flex;
  gap: 12px;
  padding: 14px;
  margin-bottom: 10px;
}
.citation__index {
  width: 24px;
  height: 24px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: 8px;
  background: var(--ca-primary-light-9);
  color: var(--ca-primary-deep);
  font-size: 13px;
  font-weight: 700;
}
.citation__body {
  min-width: 0;
}
.citation__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ca-primary-deep);
  text-decoration: none;
}
.citation__title:hover {
  text-decoration: underline;
}
.citation__org,
.citation__loc {
  font-size: 12px;
  color: var(--ca-ink-soft);
  margin-top: 2px;
}
.citation__quote {
  margin: 8px 0 0;
  padding: 8px 10px;
  border-left: 3px solid var(--ca-primary);
  background: #faf7f0;
  border-radius: 0 8px 8px 0;
  font-size: 12.5px;
  color: var(--ca-ink-soft);
  line-height: 1.6;
}
.citation-panel__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: auto;
  padding-top: 10px;
  font-size: 12px;
  color: var(--ca-ink-faint);
}
</style>
