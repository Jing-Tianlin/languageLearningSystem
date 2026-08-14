import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    rollupOptions: {
      output: {
        // 大体积第三方库单独分包，利于浏览器长期缓存与并行加载
        // 注意: Vite 8 (Rolldown) 要求 manualChunks 使用函数形式
        manualChunks(id) {
          const path = id.replace(/\\/g, '/')
          if (!path.includes('node_modules')) return undefined
          if (/[\/]vue(-router)?[\/]|[\/]pinia[\/]|[\/]@vue[\/]/.test(path)) return 'vue'
          if (/[\/]echarts[\/]|vue-echarts/.test(path)) return 'charts'
          if (/[\/]gsap[\/]/.test(path)) return 'motion'
          return undefined
        },
      },
    },
  },
})
