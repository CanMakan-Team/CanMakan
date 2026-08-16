import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.dirname(fileURLToPath(import.meta.url))
const clientRoot = path.resolve(webRoot, '..')
const mascotDrawable = path.resolve(clientRoot, 'shared/assets/mascot/drawable')
const emailFallbackMascot = path.join(mascotDrawable, 'canmakan_mascot_wave.png')

function readFileOrThrow(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Missing shared client asset: ${filePath}`)
  }
  return fs.readFileSync(filePath)
}

/**
 * Invitation emails fall back to this unhashed URL when the CID attachment is
 * missing. Keep the path stable; in-app mascot poses are imported and hashed.
 */
function emailMascotFallbackPlugin() {
  const publicPath = '/email/canmakan-mascot-wave.png'

  return {
    name: 'email-mascot-fallback',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const urlPath = request.url?.split('?')[0]
        if (urlPath !== publicPath) {
          next()
          return
        }
        response.setHeader('Content-Type', 'image/png')
        response.end(readFileOrThrow(emailFallbackMascot))
      })
    },
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: publicPath.slice(1),
        source: readFileOrThrow(emailFallbackMascot),
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), emailMascotFallbackPlugin()],
  resolve: {
    alias: {
      '@mascot': mascotDrawable,
    },
  },
  server: {
    fs: {
      allow: [clientRoot],
    },
  },
  test: {
    include: ['./src/test/**/*.{test,spec}.{ts,tsx}'],
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    css: false,
    restoreMocks: true,
    clearMocks: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      reportsDirectory: './coverage',
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/test/**', 'src/mocks/**', '**/*.d.ts'],
    },
  },
})
