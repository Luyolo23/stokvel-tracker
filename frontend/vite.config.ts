import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// In development the React dev server proxies API calls to Spring Boot on :8080.
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api': 'http://localhost:8080' } },
})
