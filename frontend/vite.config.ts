import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          'echarts': ['echarts'],
        },
      },
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api/v1/auth': {
        target: 'http://localhost:8001',
        changeOrigin: true,
      },
      '/api/v1/users': {
        target: 'http://localhost:8002',
        changeOrigin: true,
      },
      '/api/v1/admin': {
        target: 'http://localhost:8002',
        changeOrigin: true,
      },
      '/api/v1/internal': {
        target: 'http://localhost:8002',
        changeOrigin: true,
      },
      '/api/v1/courses': {
        target: 'http://localhost:8003',
        changeOrigin: true,
      },
      '/api/v1/chapters': {
        target: 'http://localhost:8003',
        changeOrigin: true,
      },
      '/api/v1/resources': {
        target: 'http://localhost:8003',
        changeOrigin: true,
      },
      '/api/v1/learn': {
        target: 'http://localhost:8004',
        changeOrigin: true,
      },
      '/api/v1/assignments': {
        target: 'http://localhost:8005',
        changeOrigin: true,
      },
      '/api/v1/submissions': {
        target: 'http://localhost:8005',
        changeOrigin: true,
      },
      '/api/v1/notifications': {
        target: 'http://localhost:8006',
        changeOrigin: true,
      },
      '/api/v1/chat': {
        target: 'http://localhost:8104',
        changeOrigin: true,
      },
      '/api/v1/media': {
        target: 'http://localhost:8007',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8006',
        ws: true,
      },
    },
  },
})
