import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // during development, proxy API calls to the Spring Boot backend
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false }
    }
  },
  build: {
    outDir: 'dist'
  }
})
