import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import ui from '@nuxt/ui/vite'
import path from 'node:path'

const traefikUrl = process.env.VITE_TRAEFIK_URL ?? 'http://localhost'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    // Nuxt UI registers `@tailwindcss/vite` itself, so the standalone Tailwind plugin is not added.
    ui({
      ui: {
        colors: {
          primary: 'cyan',
          secondary: 'amber',
        },
      },
      // Bundle only the icons Pocketr uses (plus Nuxt UI's own) from `@iconify-json/*`: no runtime Iconify API calls.
      // `globInclude` replaces the default list; `.ts` is included because some icon names live in TS modules.
      icon: {
        clientBundle: {
          scan: {
            globInclude: ['src/**/*.{vue,ts}'],
            globExclude: [
              'node_modules',
              'dist',
              'build',
              'coverage',
              'test',
              'tests',
              '.*',
              '**/__tests__/**',
              '**/*.spec.ts',
              '**/*.d.ts',
            ],
          },
        },
      },
      // `useAppTheme` (VueUse `useColorMode`, `.dark` class on <html>) stays the only color-mode controller.
      colorMode: false,
      // Auto-register Nuxt UI components only; legacy `src/components/**` keep their explicit imports.
      components: {
        dirs: [],
      },
      experimental: {
        componentDetection: true,
      },
    }),
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
