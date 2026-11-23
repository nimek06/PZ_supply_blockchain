import { createConfig, http } from 'wagmi'
import { injected } from 'wagmi/connectors'
import { appChain } from './chains'

// Tylko portfel w przeglądarce (MetaMask itp.). Bez WalletConnect, więc nie trzeba projectId.
export const wagmiConfig = createConfig({
  chains: [appChain],
  connectors: [injected()],
  transports: { [appChain.id]: http() },
})

declare module 'wagmi' {
  interface Register {
    config: typeof wagmiConfig
  }
}
