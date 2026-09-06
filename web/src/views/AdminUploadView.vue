<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '@/api/http'

interface UploadResult {
  documentId: string
  status: string
  jobId?: string
  failureReason?: string | null
}

const title = ref('')
const organization = ref('')
const sourceUrl = ref('')
const effectiveDate = ref('')
const file = ref<File | null>(null)
const uploading = ref(false)
const result = ref<UploadResult | null>(null)

const fileInput = ref<HTMLInputElement | null>(null)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  file.value = input.files?.[0] ?? null
}

async function submit() {
  if (!file.value) {
    ElMessage.warning('请选择要导入的文件')
    return
  }
  if (!title.value.trim() || !organization.value.trim()) {
    ElMessage.warning('请填写标题与发布机构')
    return
  }

  const form = new FormData()
  form.append('file', file.value)
  form.append('title', title.value.trim())
  form.append('issuingOrganization', organization.value.trim())
  if (sourceUrl.value.trim()) form.append('sourceUrl', sourceUrl.value.trim())
  if (effectiveDate.value) form.append('effectiveDate', effectiveDate.value)

  uploading.value = true
  try {
    result.value = await api.uploadForm<UploadResult>('/api/v1/admin/knowledge/documents', form)
    ElMessage.success(result.value.status === 'FAILED' ? '已受理，但解析失败' : '文档导入成功')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="admin">
    <div class="admin__card ca-card">
      <h1 class="font-display admin__title">政策文档导入</h1>
      <p class="admin__sub">仅支持 Markdown（.md）与可解析文本 PDF（.pdf），单文件不超过 10 MB；纯扫描件请转 Markdown。</p>

      <el-form label-position="top">
        <el-form-item label="文件">
          <input ref="fileInput" type="file" accept=".md,.pdf" hidden @change="onFileChange" />
          <div class="admin__file" @click="fileInput?.click()">
            <span v-if="file" class="admin__file-name">{{ file.name }}（{{ (file.size / 1024).toFixed(1) }} KB）</span>
            <span v-else class="admin__file-placeholder">点击选择文件</span>
          </div>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="title" maxlength="200" placeholder="例如：四川省推进基本养老服务体系建设实施方案" />
        </el-form-item>
        <el-form-item label="发布机构">
          <el-input v-model="organization" maxlength="200" placeholder="例如：四川省人民政府办公厅" />
        </el-form-item>
        <el-form-item label="来源链接（可选）">
          <el-input v-model="sourceUrl" maxlength="1000" placeholder="https://…" />
        </el-form-item>
        <el-form-item label="生效日期（可选）">
          <el-input v-model="effectiveDate" placeholder="YYYY-MM-DD" />
        </el-form-item>
        <el-button type="primary" :loading="uploading" @click="submit">上传并导入</el-button>
      </el-form>

      <div v-if="result" class="admin__result">
        <div class="admin__result-row">
          <span class="admin__result-label">文档 ID</span>
          <code>{{ result.documentId }}</code>
        </div>
        <div class="admin__result-row">
          <span class="admin__result-label">状态</span>
          <span :class="result.status === 'COMPLETED' ? 'ok' : result.status === 'FAILED' ? 'bad' : ''">
            {{ result.status }}
          </span>
        </div>
        <div v-if="result.failureReason" class="admin__result-row">
          <span class="admin__result-label">原因</span>
          <span class="bad">{{ result.failureReason }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin {
  height: 100%;
  overflow-y: auto;
  padding: 28px 32px;
}
.admin__card {
  max-width: 560px;
  margin: 0 auto;
  padding: 28px 30px;
}
.admin__title {
  font-size: 22px;
  margin: 0 0 6px;
}
.admin__sub {
  font-size: 13.5px;
  color: var(--ca-ink-soft);
  margin: 0 0 20px;
}
.admin__file {
  width: 100%;
  padding: 22px;
  border: 1.5px dashed var(--ca-line);
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  color: var(--ca-ink-soft);
  transition: border-color 0.2s;
}
.admin__file:hover {
  border-color: var(--ca-primary);
}
.admin__file-name {
  color: var(--ca-primary-deep);
  font-size: 14px;
}
.admin__file-placeholder {
  font-size: 14px;
}
.admin__result {
  margin-top: 20px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #faf7f0;
  font-size: 13px;
}
.admin__result-row {
  display: flex;
  gap: 12px;
  padding: 4px 0;
}
.admin__result-label {
  color: var(--ca-ink-soft);
  min-width: 64px;
}
.admin__result code {
  word-break: break-all;
}
.ok {
  color: var(--ca-success);
}
.bad {
  color: var(--ca-danger);
}
</style>
