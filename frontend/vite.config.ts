import react from '@vitejs/plugin-react'
import { defaultExclude, defineConfig } from 'vitest/config'

const backendProxy = {
  target: process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
  changeOrigin: false,
}

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': backendProxy,
      '/health': backendProxy,
      '/swagger-ui': backendProxy,
      '/v3/api-docs': backendProxy,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    exclude: [...defaultExclude, 'e2e/**'],
  },
})
