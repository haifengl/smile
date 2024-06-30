import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import svgr from "vite-plugin-svgr";

// https://vitejs.dev/config/
export default defineConfig({
  base: '/chat',
  build: {
    outDir: '../serve/src/universal/chat'
  },
  plugins: [react(), svgr()],
})
