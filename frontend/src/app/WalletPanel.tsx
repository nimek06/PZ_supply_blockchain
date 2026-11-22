import { useConnect, useConnection, useConnectors, useDisconnect, useSwitchChain } from 'wagmi'
import { Button } from '../components/ui/Button'
import { shortAddress } from '../lib/format'
import type { Role } from '../lib/api/types'
import { isUserRejection } from '../lib/blockchain/walletErrors'
import { config } from '../lib/config'
import { useSession } from './session'

const ROLE_NAMES: Record<Role, string> = {
  OPERATOR_ROLE: 'operator',
  QA_INSPECTOR_ROLE: 'operator QA',
  RELAYER_ROLE: 'relayer',
  DEFAULT_ADMIN_ROLE: 'admin',
}

function Message({ children, tone = 'danger' }: { children: string; tone?: 'danger' | 'muted' }) {
  return <p className={`text-xs ${tone === 'danger' ? 'text-rose-700' : 'text-stone-500'}`}>{children}</p>
}

// Połączenie portfela to nie logowanie. Do API uwierzytelnia dopiero podpis SIWE.
export function WalletPanel() {
  const { address, chainId, isConnected } = useConnection()
  const connectors = useConnectors()
  const connect = useConnect()
  const disconnect = useDisconnect()
  const switchChain = useSwitchChain()
  const { state, signIn, signOut } = useSession()

  const injected = connectors[0]

  if (!isConnected || !address) {
    return (
      <div className="flex flex-col items-start gap-1 sm:items-end">
        <Button variant="secondary" onClick={() => injected && connect.mutate({ connector: injected })} disabled={!injected || connect.isPending}>
          {connect.isPending ? 'Łączenie…' : 'Połącz portfel'}
        </Button>
        {!injected && <Message>Brak portfela w przeglądarce (np. MetaMask).</Message>}
        {connect.error && <Message>{isUserRejection(connect.error) ? 'Odrzucono połączenie w portfelu.' : connect.error.message}</Message>}
      </div>
    )
  }

  const wrongChain = chainId !== config.chainId
  const signedIn = state.status === 'signed-in'
  const roles = signedIn ? state.context.roles.map((role) => ROLE_NAMES[role]).join(', ') : ''

  return (
    <div className="flex flex-col items-start gap-1 sm:items-end">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-xs text-stone-600" title={address}>
          {shortAddress(address)}
        </span>
        {wrongChain ? (
          <Button onClick={() => switchChain.mutate({ chainId: config.chainId })} disabled={switchChain.isPending}>
            Przełącz na sieć {config.chainId}
          </Button>
        ) : signedIn ? (
          <Button variant="secondary" onClick={() => void signOut()}>
            Wyloguj
          </Button>
        ) : (
          <Button onClick={() => void signIn()} disabled={state.status === 'signing-in'}>
            {state.status === 'signing-in' ? 'Podpisz w portfelu…' : 'Zaloguj'}
          </Button>
        )}
        <Button variant="ghost" onClick={() => disconnect.mutate()}>
          Rozłącz
        </Button>
      </div>
      {wrongChain && <Message>{`Portfel jest na sieci ${chainId}, aplikacja działa na ${config.chainId}.`}</Message>}
      {signedIn && <Message tone="muted">{roles ? `Zalogowany · ${roles}` : 'Zalogowany · bez ról'}</Message>}
      {state.status === 'error' && <Message>{state.message}</Message>}
    </div>
  )
}
