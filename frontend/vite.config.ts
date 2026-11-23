/// <reference types="vitest/config" />
import basicSsl from '@vitejs/plugin-basic-ssl'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { rmSync } from 'node:fs'
import { resolve } from 'node:path'
import { defineConfig, loadEnv, type Plugin } from 'vite'

// public/README.md to instrukcja dla zespołu, nie plik aplikacji, więc nie trafia do dist.
function dropPublicReadme(): Plugin {
  let outDir = 'dist'
  return {
    name: 'drop-public-readme',
    apply: 'build',
    configResolved(resolved) {
      outDir = resolve(resolved.root, resolved.build.outDir)
    },
    closeBundle() {
      rmSync(resolve(outDir, 'README.md'), { force: true })
    },
  }
}

// `npm run dev` - http://localhost:5173, tylko ten komputer.
// `npm run dev:https` - HTTPS z certyfikatem deweloperskim, widoczne w sieci lokalnej (telefon, kamera).
// /api, /rpc i /ipfs idą przez proxy, więc przeglądarka (także telefon) zostaje na jednym originie,
// nie potrzeba CORS, a Anvil i Kubo dalej słuchają tylko na 127.0.0.1 komputera.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const https = mode === 'https'

  return {
    plugins: [react(), tailwindcss(), dropPublicReadme(), ...(https ? [basicSsl()] : [])],
    server: {
      host: https ? true : 'localhost',
      port: 5173,
      strictPort: true,
      proxy: {
        '/api': { target: env.BACKEND_URL || 'http://localhost:8080' },
        '/rpc': { target: env.ANVIL_URL || 'http://127.0.0.1:8545', rewrite: () => '/' },
        // changeOrigin: Kubo przy Host=localhost przekierowuje na bramkę subdomenową, a tu chcemy ścieżkową
        '/ipfs': { target: env.IPFS_GATEWAY_URL || 'http://127.0.0.1:8081', changeOrigin: true },
      },
    },
    build: {
      rolldownOptions: {
        output: {
          // biblioteki portfela i kryptografii w osobnym pliku, zmieniają się rzadziej niż kod aplikacji
          codeSplitting: {
            groups: [{ name: 'web3', test: /node_modules[\\/](viem|wagmi|@wagmi|ox|@noble|@scure|abitype)[\\/]/ }],
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      include: ['src/**/*.test.{ts,tsx}', 'tests/**/*.test.{ts,tsx}'],
      setupFiles: ['tests/setup.ts'],
      restoreMocks: true,
    },
  }
})
