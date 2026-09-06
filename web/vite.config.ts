import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地开发：Vite 把 /api 与 /health 代理到 Nginx（8088），由 Nginx 转发到 Java。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://127.0.0.1:8088', changeOrigin: true },
      '/health': { target: 'http://127.0.0.1:8088', changeOrigin: true },
    },
  },
})
