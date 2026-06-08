// import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig as defineVitestConfig } from 'vitest/config'

export default defineVitestConfig({
  plugins: [
    tailwindcss(),
    react()
  ],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      exclude: ['node_modules/', 'src/assets/'],
    },
  },
  server: {
    port: 5173,
  }
})
