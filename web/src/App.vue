<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/stores/auth'
import { Calendar, ChatDotRound, SwitchButton, UploadFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

function logout() {
  auth.clear()
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container v-if="auth.isAuthed" class="shell">
    <el-header class="topbar" height="64px">
      <div class="topbar__brand">
        <span class="ca-brand-mark">
          <svg viewBox="0 0 24 24" width="19" height="19" fill="none" stroke="currentColor"
            stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <path d="M12 3.2C8.4 5.9 5.5 9.4 5.5 13.3a6.5 6.5 0 0 0 13 0c0-3.9-2.9-7.4-6.5-10.1Z" />
            <path d="M12 3.2v10" />
          </svg>
        </span>
        <div class="topbar__brand-text">
          <div class="font-display topbar__title">CareAgent</div>
          <div class="topbar__sub">安心养老助手</div>
        </div>
      </div>

      <nav class="topbar__nav">
        <router-link :to="{ name: 'chat' }" class="nav-link" :class="{ active: route.name === 'chat' }">
          <el-icon><ChatDotRound /></el-icon>
          <span>咨询</span>
        </router-link>
        <router-link :to="{ name: 'appointments' }" class="nav-link"
          :class="{ active: route.name === 'appointments' }">
          <el-icon><Calendar /></el-icon>
          <span>我的预约</span>
        </router-link>
        <router-link v-if="auth.isAdmin" :to="{ name: 'admin-upload' }" class="nav-link"
          :class="{ active: route.name === 'admin-upload' }">
          <el-icon><UploadFilled /></el-icon>
          <span>文档导入</span>
        </router-link>
      </nav>

      <div class="topbar__right">
        <span class="topbar__user">{{ auth.username }}</span>
        <el-button text :icon="SwitchButton" @click="logout">退出</el-button>
      </div>
    </el-header>

    <el-main class="shell__main">
      <router-view />
    </el-main>
  </el-container>

  <router-view v-else />
</template>

<style scoped>
.shell {
  height: 100%;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--ca-line);
}
.topbar__brand {
  display: flex;
  align-items: center;
  gap: 12px;
}
.topbar__brand-text {
  line-height: 1.2;
}
.topbar__title {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.topbar__sub {
  font-size: 12px;
  color: var(--ca-ink-soft);
}
.topbar__nav {
  display: flex;
  gap: 6px;
  flex: 1;
}
.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 16px;
  border-radius: 999px;
  color: var(--ca-ink-soft);
  text-decoration: none;
  font-size: 15px;
  transition: background 0.2s, color 0.2s;
}
.nav-link:hover {
  color: var(--ca-primary);
  background: var(--ca-primary-light-9);
}
.nav-link.active {
  color: #fff;
  background: var(--ca-primary);
}
.topbar__right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.topbar__user {
  font-size: 14px;
  color: var(--ca-ink-soft);
}
.shell__main {
  padding: 0;
  overflow: hidden;
}
@media (max-width: 720px) {
  .topbar {
    gap: 12px;
    padding: 0 14px;
  }
  .topbar__sub,
  .topbar__user {
    display: none;
  }
  .nav-link span {
    display: none;
  }
  .nav-link {
    padding: 8px 10px;
  }
}
</style>
