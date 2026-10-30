import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const backendPort = Number(process.env.CAMPUS_BACKEND_PORT ?? '8080')
if (!Number.isInteger(backendPort) || backendPort < 1024 || backendPort > 65535) throw new Error('invalid local backend port')

export default defineConfig({
  plugins: [vue()],
  server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: { '/api': { target: `http://127.0.0.1:${backendPort}`, changeOrigin: true } } },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
})
