import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 将 /api 请求代理到后端服务
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
