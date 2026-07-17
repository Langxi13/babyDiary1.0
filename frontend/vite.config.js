import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readFileSync } from 'node:fs'

const apiTarget = process.env.VITE_DEV_API_TARGET || 'http://localhost:10002'
const packageInfo = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8'))

export default defineConfig({
  plugins: [vue()],
  define: {
    __APP_VERSION__: JSON.stringify(packageInfo.version)
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('/node_modules/vue/') || id.includes('/node_modules/vue-router/') || id.includes('/node_modules/pinia/')) {
            return 'vue-vendor'
          }
          if (id.includes('/node_modules/axios/')) {
            return 'axios'
          }
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    include: ['src/**/*.spec.js'],
    server: {
      deps: {
        inline: [/element-plus/]
      }
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'json-summary'],
      include: [
        'src/config/**/*.js',
        'src/components/common/NavBar.vue',
        'src/views/diary/Album.vue'
      ],
      thresholds: {
        lines: 50,
        functions: 30,
        branches: 30,
        statements: 45
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true
      },
      '/images': {
        target: apiTarget,
        changeOrigin: true
      }
    }
  }
})
