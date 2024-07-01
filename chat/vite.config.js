import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  base: '/chat',
  build: {
    outDir: '../serve/src/universal/chat'
  },
  plugins: [react()],
})
