import type { Address, Hex } from 'viem'
import { api } from './client'
import type { AuthChallenge, AuthSession, SessionContext } from './types'

// SIWE: challenge -> podpis w portfelu -> token Bearer.

export async function createChallenge(address: Address): Promise<AuthChallenge> {
  const { data } = await api.post<AuthChallenge>('/auth/challenges', { address })
  return data
}

export async function createSession(challenge: AuthChallenge, signature: Hex): Promise<AuthSession> {
  const { data } = await api.post<AuthSession>('/auth/sessions', {
    challengeId: challenge.challengeId,
    message: challenge.message,
    signature,
  })
  return data
}

export async function getCurrentSession(): Promise<SessionContext> {
  const { data } = await api.get<SessionContext>('/auth/sessions/current')
  return data
}

/** Unieważnia podany token w backendzie. Token jest podany wprost, bo w pamięci klienta już go nie ma. */
export async function revokeSession(token: string): Promise<void> {
  await api.delete('/auth/sessions/current', { headers: { Authorization: `Bearer ${token}` } })
}
