import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UserRejectedRequestError } from 'viem'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, setBearerToken, takeBearerToken } from '../lib/api/client'
import type { SessionContext } from '../lib/api/types'
import { SessionProvider, useSession } from './session'

// Portfel i backend są podstawione: sprawdzamy tylko przebieg sesji SIWE.

const ADDRESS = '0x90F79bf6EB2c4f870365E785982E1f101E93b906'
const OTHER = '0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC'

const wallet = vi.hoisted(() => ({
  connection: { address: undefined as string | undefined, chainId: undefined as number | undefined },
  onChange: null as null | ((c: { address?: string; chainId?: number }) => void),
  signMessage: vi.fn(),
}))
const auth = vi.hoisted(() => ({
  createChallenge: vi.fn(),
  createSession: vi.fn(),
  getCurrentSession: vi.fn(),
  revokeSession: vi.fn(),
}))

vi.mock('wagmi', () => ({
  useConnection: () => wallet.connection,
  useSignMessage: () => ({ mutateAsync: wallet.signMessage }),
}))
vi.mock('wagmi/actions', () => ({
  getConnection: () => wallet.connection,
  watchConnection: (_config: unknown, { onChange }: { onChange: (c: { address?: string; chainId?: number }) => void }) => {
    wallet.onChange = onChange
    return () => {
      wallet.onChange = null
    }
  },
}))
vi.mock('../lib/blockchain/wagmiConfig', () => ({ wagmiConfig: {} }))
vi.mock('../lib/api/authApi', () => auth)

const context: SessionContext = {
  address: ADDRESS.toLowerCase(),
  roles: ['OPERATOR_ROLE'],
  expiresAt: 1_900_000_000,
  chainId: 31337,
  verifyingContract: '0x5fbdb2315678afecb367f032d93f642f64180aa3',
}

function Probe() {
  const { state, signIn, signOut } = useSession()
  return (
    <div>
      <p data-testid="status">{state.status}</p>
      {state.status === 'error' && <p data-testid="error">{state.message}</p>}
      <button onClick={() => void signIn()}>in</button>
      <button onClick={() => void signOut()}>out</button>
    </div>
  )
}

function setup() {
  render(
    <SessionProvider>
      <Probe />
    </SessionProvider>,
  )
}

beforeEach(() => {
  takeBearerToken()
  wallet.connection = { address: ADDRESS, chainId: 31337 }
  wallet.signMessage.mockReset().mockResolvedValue('0xsig')
  auth.createChallenge.mockReset().mockResolvedValue({ challengeId: 'c-1', message: 'localhost wants you to sign in', expiresAt: 1 })
  auth.createSession.mockReset().mockResolvedValue({ token: 'tok-1', tokenType: 'Bearer', address: ADDRESS, expiresAt: 1, idleTimeoutSeconds: 1800 })
  auth.getCurrentSession.mockReset().mockResolvedValue(context)
  auth.revokeSession.mockReset().mockResolvedValue(undefined)
})

describe('SIWE', () => {
  it('logowanie: challenge, podpis wiadomości z backendu, token w pamięci', async () => {
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('signed-in'))
    expect(auth.createChallenge).toHaveBeenCalledWith(ADDRESS)
    expect(wallet.signMessage).toHaveBeenCalledWith({ message: 'localhost wants you to sign in' })
    expect(takeBearerToken()).toBe('tok-1')
  })

  it('odrzucony podpis to błąd, nie sesja', async () => {
    wallet.signMessage.mockRejectedValue(new UserRejectedRequestError(new Error('denied')))
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('error')).toHaveTextContent('Podpis odrzucony w portfelu'))
    expect(auth.createSession).not.toHaveBeenCalled()
    expect(takeBearerToken()).toBeNull()
  })

  it('zła sieć w portfelu blokuje logowanie', async () => {
    wallet.connection = { address: ADDRESS, chainId: 1 }
    setup()
    await userEvent.click(screen.getByText('in'))
    expect(screen.getByTestId('error')).toHaveTextContent('Przełącz portfel na sieć 31337')
    expect(auth.createChallenge).not.toHaveBeenCalled()
  })

  it('backend na innym kontrakcie: sesja od razu unieważniona', async () => {
    auth.getCurrentSession.mockResolvedValue({ ...context, verifyingContract: '0x0000000000000000000000000000000000000001' })
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('error')).toHaveTextContent(/kontraktu/))
    expect(auth.revokeSession).toHaveBeenCalledWith('tok-1')
    expect(takeBearerToken()).toBeNull()
  })

  it('zmiana konta w portfelu kończy sesję i unieważnia token', async () => {
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('signed-in'))

    act(() => wallet.onChange?.({ address: OTHER, chainId: 31337 }))
    expect(screen.getByTestId('error')).toHaveTextContent('Zmieniono konto w portfelu')
    expect(auth.revokeSession).toHaveBeenCalledWith('tok-1')
    expect(takeBearerToken()).toBeNull()
  })

  it('zmiana sieci kończy sesję', async () => {
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('signed-in'))
    act(() => wallet.onChange?.({ address: ADDRESS, chainId: 80002 }))
    expect(screen.getByTestId('error')).toHaveTextContent('Zmieniono sieć')
  })

  it('wylogowanie czyści token i unieważnia go w backendzie', async () => {
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('signed-in'))
    await userEvent.click(screen.getByText('out'))
    expect(screen.getByTestId('status')).toHaveTextContent('signed-out')
    expect(auth.revokeSession).toHaveBeenCalledWith('tok-1')
    expect(takeBearerToken()).toBeNull()
  })

  it('błąd backendu przy logowaniu nie zostawia tokenu', async () => {
    auth.getCurrentSession.mockRejectedValue(new ApiError('Backend niedostępny', 503, 'UNAVAILABLE', 'x'))
    setup()
    await userEvent.click(screen.getByText('in'))
    await waitFor(() => expect(screen.getByTestId('error')).toHaveTextContent('Logowanie nieudane: Backend niedostępny'))
    expect(auth.revokeSession).toHaveBeenCalledWith('tok-1')
    setBearerToken(null)
  })
})
