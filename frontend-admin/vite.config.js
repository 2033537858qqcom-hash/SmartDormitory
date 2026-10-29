import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/uploads': { target: 'http://localhost:7000', changeOrigin: true },
      '/api': {
        target: 'http://localhost:7000',
        changeOrigin: true
      }
    }
  }
})
