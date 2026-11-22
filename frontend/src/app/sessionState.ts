import type { SessionContext } from '../lib/api/types'

// Stan sesji SIWE bez Reacta, żeby dało się go łatwo przetestować.

export type SessionState =
  | { status: 'signed-out' }
  | { status: 'signing-in' }
  | { status: 'signed-in'; context: SessionContext }
  | { status: 'error'; message: string }

export interface WalletView {
  address: string | undefined
  chainId: number | undefined
}

/**
 * Sesja należy do konkretnego adresu i sieci. Po zmianie konta albo sieci w portfelu
 * trzeba ją porzucić, zanim cokolwiek zostanie wysłane z cudzym tokenem.
 */
export function sessionMismatch(state: SessionState, wallet: WalletView, expectedChainId: number): string | null {
  if (state.status !== 'signed-in') return null
  if (!wallet.address) return 'Portfel został rozłączony'
  if (wallet.address.toLowerCase() !== state.context.address.toLowerCase()) {
    return 'Zmieniono konto w portfelu'
  }
  if (wallet.chainId !== expectedChainId) return 'Zmieniono sieć w portfelu'
  return null
}

/** Backend musi pracować na tej samej sieci i kontrakcie co frontend, inaczej podpisy nie będą pasować. */
export function deploymentMismatch(
  context: SessionContext,
  expectedChainId: number,
  expectedContract: string,
): string | null {
  if (context.chainId !== expectedChainId) {
    return `Backend działa na sieci ${context.chainId}, a frontend na ${expectedChainId}`
  }
  if (context.verifyingContract.toLowerCase() !== expectedContract.toLowerCase()) {
    return `Backend używa kontraktu ${context.verifyingContract}, a frontend ${expectedContract}`
  }
  return null
}
