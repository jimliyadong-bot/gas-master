import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 本地开发：/api 代理到后端 9090；生产由 Nginx 静态托管 h5 并反代 /api
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5300,
    proxy: {
      '/api': {
        target: 'http://localhost:9090',
        changeOrigin: true,
        // 保持授权回跳在前端端口，避免后端绝对Location把浏览器带到9090/login。
        autoRewrite: true
      }
    }
  }
})
