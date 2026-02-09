import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

const traefikUrl = process.env.VITE_TRAEFIK_URL ?? 'http://localhost'

// https://vite.dev/config/
export default defineConfig({
  base: '/frontend/',
  plugins: [
    vue(),
    vueDevTools(),
    tailwindcss(),
    {
      name: 'traefik-info',
      configureServer(server) {
        const originalPrintUrls = server.printUrls
        server.printUrls = () => {
          originalPrintUrls()
          server.config.logger.info(`  ➜  Primary Access URL: ${traefikUrl}`)
        }
      },
    },
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    outDir: 'dist',
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    origin: 'http://localhost',
    allowedHosts: ['localhost', 'host.docker.internal'],
    proxy: {
      '/api': {
        target: 'http://host.docker.internal:8081',
        changeOrigin: true,
        secure: false,
        cookieDomainRewrite: 'localhost',
      },
    },
  },
})
