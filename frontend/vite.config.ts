import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      // API_PROXY lets a second dev server talk to another backend, e.g. http://localhost:8081
      '/api': process.env.API_PROXY ?? 'http://localhost:8080',
    },
  },
})
