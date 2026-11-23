import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { createQueryClient } from '../src/app/queryClient'
import { SessionContextValue, type SessionApi } from '../src/app/session'
import type { SessionState } from '../src/app/sessionState'

interface Options {
  route?: string
  session?: SessionState
}

// Render z routerem, świeżym QueryClient i podstawioną sesją, bez portfela.
export function renderWithProviders(ui: ReactNode, { route = '/', session = { status: 'signed-out' } }: Options = {}) {
  const queryClient = createQueryClient()
  const sessionApi: SessionApi = { state: session, signIn: async () => {}, signOut: async () => {} }
  return render(
    <QueryClientProvider client={queryClient}>
      <SessionContextValue.Provider value={sessionApi}>
        <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
      </SessionContextValue.Provider>
    </QueryClientProvider>,
  )
}
