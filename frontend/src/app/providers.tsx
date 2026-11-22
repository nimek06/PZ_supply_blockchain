import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { WagmiProvider } from 'wagmi'
import { wagmiConfig } from '../lib/blockchain/wagmiConfig'
import { createQueryClient } from './queryClient'
import { SessionProvider } from './session'

export function Providers({ children }: { children: ReactNode }) {
  // Jeden QueryClient na całe życie aplikacji, nie nowy przy każdym renderze.
  const [queryClient] = useState(createQueryClient)
  return (
    <WagmiProvider config={wagmiConfig}>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>{children}</SessionProvider>
      </QueryClientProvider>
    </WagmiProvider>
  )
}
