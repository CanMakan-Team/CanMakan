import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.dirname(fileURLToPath(import.meta.url))
const mascotDrawable = path.resolve(webRoot, '../shared/assets/mascot/drawable')

const SHARED_MASCOT_PUBLIC_FILES = [
  { file: 'canmakan_mascot_wave.png', publicPath: '/mascot/canmakan-mascot-wave.png' },
  { file: 'canmakan_mascot_scan.png', publicPath: '/mascot/canmakan-mascot-scan.png' },
  { file: 'canmakan_mascot_safe.png', publicPath: '/mascot/canmakan-mascot-safe.png' },
  { file: 'canmakan_mascot_warning.png', publicPath: '/mascot/canmakan-mascot-warning.png' },
  { file: 'canmakan_mascot_unsafe.png', publicPath: '/mascot/canmakan-mascot-unsafe.png' },
  { file: 'canmakan_mascot_wave.png', publicPath: '/email/canmakan-mascot-wave.png' },
]

function readSharedMascot(fileName) {
  const filePath = path.join(mascotDrawable, fileName)
  if (!fs.existsSync(filePath)) {
    throw new Error(`Missing shared mascot asset: ${filePath}`)
  }
  return fs.readFileSync(filePath)
}

function sharedMascotPlugin() {
  const byPublicPath = new Map(
    SHARED_MASCOT_PUBLIC_FILES.map((entry) => [entry.publicPath, entry.file]),
  )

  return {
    name: 'shared-mascot-assets',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        const urlPath = request.url?.split('?')[0]
        const fileName = urlPath ? byPublicPath.get(urlPath) : undefined
        if (!fileName) {
          next()
          return
        }
        response.setHeader('Content-Type', 'image/png')
        response.end(readSharedMascot(fileName))
      })
    },
    generateBundle() {
      const emitted = new Set()
      for (const entry of SHARED_MASCOT_PUBLIC_FILES) {
        if (emitted.has(entry.publicPath)) continue
        emitted.add(entry.publicPath)
        this.emitFile({
          type: 'asset',
          fileName: entry.publicPath.slice(1),
          source: readSharedMascot(entry.file),
        })
      }
    },
  }
}

export default defineConfig({
  plugins: [react(), sharedMascotPlugin()],
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
