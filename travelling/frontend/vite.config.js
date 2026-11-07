import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  // 前端页面挂在 /travelling 路径下发布（后端托管 dist 时即 http://localhost:8080/travelling/）
  base: '/travelling/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/images': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
