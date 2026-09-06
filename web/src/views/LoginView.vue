<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/http'
import { auth } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const form = reactive({ username: 'demo_user', password: 'demo-user-2026' })
const loading = ref(false)

function fill(user: string, pass: string) {
  form.username = user
  form.password = pass
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const res = await login(form.username.trim(), form.password)
    auth.setSession(res.accessToken, res.role, form.username.trim())
    ElMessage.success('登录成功')
    router.replace((route.query.redirect as string) || { name: 'chat' })
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="login__card ca-card">
      <div class="login__brand">
        <span class="ca-brand-mark login__mark">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor"
            stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 3.2C8.4 5.9 5.5 9.4 5.5 13.3a6.5 6.5 0 0 0 13 0c0-3.9-2.9-7.4-6.5-10.1Z" />
            <path d="M12 3.2v10" />
          </svg>
        </span>
      </div>
      <h1 class="font-display login__title">CareAgent</h1>
      <p class="login__sub">面向老人及家属的养老政策问答与上门服务预约</p>

      <el-form label-position="top" size="large" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password
            autocomplete="current-password" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="login__submit" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <div class="login__demo">
        <div class="login__demo-title">演示账号（点击填充）</div>
        <button type="button" class="login__chip" @click="fill('demo_user', 'demo-user-2026')">
          <span class="login__chip-role">家属</span> demo_user
        </button>
        <button type="button" class="login__chip" @click="fill('demo_admin', 'demo-admin-2026')">
          <span class="login__chip-role admin">管理员</span> demo_admin
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}
.login__card {
  width: 100%;
  max-width: 400px;
  padding: 40px 36px 32px;
}
.login__brand {
  display: flex;
  justify-content: center;
  margin-bottom: 18px;
}
.login__mark {
  width: 46px;
  height: 46px;
  border-radius: 14px;
}
.login__title {
  text-align: center;
  font-size: 30px;
  margin: 0 0 6px;
  letter-spacing: 1px;
}
.login__sub {
  text-align: center;
  color: var(--ca-ink-soft);
  font-size: 14px;
  margin: 0 0 28px;
}
.login__submit {
  width: 100%;
  margin-top: 4px;
}
.login__demo {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px dashed var(--ca-line);
}
.login__demo-title {
  font-size: 13px;
  color: var(--ca-ink-faint);
  margin-bottom: 10px;
}
.login__chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 8px 8px 0;
  padding: 7px 12px;
  border: 1px solid var(--ca-line);
  border-radius: 999px;
  background: var(--ca-fill-light, #faf7f0);
  color: var(--ca-ink);
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.2s, transform 0.1s;
}
.login__chip:hover {
  border-color: var(--ca-primary);
  transform: translateY(-1px);
}
.login__chip-role {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--ca-primary-light-9);
  color: var(--ca-primary-deep);
  font-size: 12px;
}
.login__chip-role.admin {
  background: #fbe9dd;
  color: var(--ca-accent-deep);
}
</style>
