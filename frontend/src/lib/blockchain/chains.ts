import { defineChain } from 'viem'
import { anvil } from 'viem/chains'
import { config } from '../config'

// Jedyna sieć aplikacji (DEC-17). Adres RPC z konfiguracji, bo telefon nie widzi localhost komputera.
export const appChain = defineChain({
  ...anvil,
  id: config.chainId,
  rpcUrls: { default: { http: [config.rpcUrl] } },
})
