import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { AntDesignVueResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  base: './',
  plugins: [
    vue(),
    AutoImport({
      resolvers: [AntDesignVueResolver()]
    }),
    Components({
      resolvers: [AntDesignVueResolver()]
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '/src'),
      '@assets': path.resolve(__dirname, '/src/assets')
    }
  },
  server: {
    port: 18000
  },
  css: {
    preprocessorOptions: {
      less: {
        javascriptEnabled: true,
        modifyVars: {
          'font-size-base': '13px',
          'input-bg': '#0a1429',
          'input-border-color': '#354971',
          'btn-default-color': '#0a1429',
          'radio-button-bg': '#0a1429!important',
          'radio-button-checked-bg': '#354971!important',
          'radio-button-color': 'rgba(226, 242, 255, 0.85)!important'
        }
      }
    }
  },
  optimizeDeps: {
    // @iconify/iconify: The dependency is dynamically and virtually loaded by @purge-icons/generated, so it needs to be specified explicitly
    include: ['@iconify/iconify', 'ant-design-vue/es/locale/zh_CN', 'ant-design-vue/es/locale/en_US'],
    exclude: ['vue-demi']
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    assetsInlineLimit: 4096,
    chunkSizeWarningLimit: 1500,
    cssCodeSplit: true,
    sourcemap: false,
    target: ['edge90', 'chrome106', 'firefox90', 'safari15'],
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true
      }
    },
    rollupOptions: {
      output: {
        chunkFileNames: 'static/js/[name]-[hash].js',
        entryFileNames: 'static/js/[name]-[hash].js',
        assetFileNames: 'static/css/[name]-[hash].[ext]',
        manualChunks(id) {
          if (id.includes('node_modules')) {
            return id
                .toString()
                .split('node_modules/')[1]
                .split('/')[0]
                .toString()
          }
        }
      }
    }
  }
})
