import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useConnection, useSignMessage } from 'wagmi'
import { getConnection, watchConnection } from 'wagmi/actions'
import { createChallenge, createSession, getCurrentSession, revokeSession } from '../lib/api/authApi'
import { ApiError, onUnauthorized, setBearerToken, takeBearerToken, toApiError } from '../lib/api/client'
import { config } from '../lib/config'
import { isUserRejection } from '../lib/blockchain/walletErrors'
import { wagmiConfig } from '../lib/blockchain/wagmiConfig'
import { deploymentMismatch, sessionMismatch, type SessionState } from './sessionState'

export interface SessionApi {
  state: SessionState
  signIn: () => Promise<void>
  signOut: () => Promise<void>
}

// Eksport tylko dla testów (podstawienie sesji bez portfela).
// eslint-disable-next-line react-refresh/only-export-components
export const SessionContextValue = createContext<SessionApi | null>(null)

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<SessionState>({ status: 'signed-out' })
  const stateRef = useRef(state)
  useEffect(() => {
    stateRef.current = state
  }, [state])
  const { address, chainId } = useConnection()
  const { mutateAsync: signMessage } = useSignMessage()
  const attempt = useRef(0)

  // Czyści token od razu i unieważnia go w backendzie w tle. Zwraca obietnicę unieważnienia.
  const dropSession = useCallback((next: SessionState) => {
    attempt.current++
    const token = takeBearerToken()
    setState(next)
    if (!token) return Promise.resolve()
    return revokeSession(token).catch((error: unknown) => {
      // 401 znaczy, że backend już tego tokenu nie zna.
      if (!(error instanceof ApiError && error.status === 401)) console.warn('unieważnienie sesji nieudane', error)
    })
  }, [])

  const signIn = useCallback(async () => {
    if (!address) {
      setState({ status: 'error', message: 'Najpierw połącz portfel' })
      return
    }
    if (chainId !== config.chainId) {
      setState({ status: 'error', message: `Przełącz portfel na sieć ${config.chainId}` })
      return
    }
    const current = ++attempt.current
    setState({ status: 'signing-in' })
    try {
      const challenge = await createChallenge(address)
      const signature = await signMessage({ message: challenge.message })
      const session = await createSession(challenge, signature)
      if (current !== attempt.current) return
      setBearerToken(session.token)
      const context = await getCurrentSession()
      const deployment = deploymentMismatch(context, config.chainId, config.contractAddress)
      if (deployment) {
        void dropSession({ status: 'error', message: deployment })
        return
      }
      if (current !== attempt.current) return
      // Konto mogło się zmienić w trakcie podpisu, zanim zaczęliśmy słuchać zmian.
      const signedIn: SessionState = { status: 'signed-in', context }
      const changed = sessionMismatch(signedIn, getConnection(wagmiConfig), config.chainId)
      if (changed) {
        void dropSession({ status: 'error', message: `${changed}, zaloguj się ponownie` })
        return
      }
      setState(signedIn)
    } catch (error) {
      if (current !== attempt.current) return
      const message = isUserRejection(error)
        ? 'Podpis odrzucony w portfelu, nie zalogowano'
        : `Logowanie nieudane: ${toApiError(error).message}`
      void dropSession({ status: 'error', message })
    }
  }, [address, chainId, signMessage, dropSession])

  const signOut = useCallback(() => dropSession({ status: 'signed-out' }), [dropSession])

  // Zmiana konta albo sieci w portfelu kończy sesję od razu, zanim poleci żądanie z cudzym tokenem.
  useEffect(
    () =>
      watchConnection(wagmiConfig, {
        onChange(connection) {
          const mismatch = sessionMismatch(stateRef.current, connection, config.chainId)
          if (mismatch) void dropSession({ status: 'error', message: `${mismatch}, zaloguj się ponownie` })
        },
      }),
    [dropSession],
  )

  // 401 na żądaniu z tokenem: sesja wygasła albo została unieważniona.
  useEffect(
    () =>
      onUnauthorized(() => {
        if (stateRef.current.status === 'signed-in') {
          void dropSession({ status: 'error', message: 'Sesja wygasła, zaloguj się ponownie' })
        }
      }),
    [dropSession],
  )

  const value = useMemo(() => ({ state, signIn, signOut }), [state, signIn, signOut])
  return <SessionContextValue.Provider value={value}>{children}</SessionContextValue.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSession(): SessionApi {
  const value = useContext(SessionContextValue)
  if (!value) throw new Error('useSession poza SessionProvider')
  return value
}
