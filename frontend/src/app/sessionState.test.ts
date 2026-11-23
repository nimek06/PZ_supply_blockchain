import { describe, expect, it } from 'vitest'
import type { SessionContext } from '../lib/api/types'
import { deploymentMismatch, sessionMismatch, type SessionState } from './sessionState'

const context: SessionContext = {
  address: '0x70997970C51812dc3A010C7d01b50e0d17dc79C8',
  roles: ['OPERATOR_ROLE'],
  expiresAt: 1_900_000_000,
  chainId: 31337,
  verifyingContract: '0x5FbDB2315678afecb367f032d93F642f64180aa3',
}
const signedIn: SessionState = { status: 'signed-in', context }

describe('sessionMismatch', () => {
  it('ta sama osoba i sieć, wielkość liter adresu bez znaczenia', () => {
    expect(sessionMismatch(signedIn, { address: context.address.toLowerCase(), chainId: 31337 }, 31337)).toBeNull()
  })
  it('inne konto kończy sesję', () => {
    expect(sessionMismatch(signedIn, { address: '0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC', chainId: 31337 }, 31337)).toMatch(/konto/)
  })
  it('inna sieć kończy sesję', () => {
    expect(sessionMismatch(signedIn, { address: context.address, chainId: 1 }, 31337)).toMatch(/sieć/)
  })
  it('rozłączony portfel kończy sesję', () => {
    expect(sessionMismatch(signedIn, { address: undefined, chainId: undefined }, 31337)).toMatch(/rozłączony/)
  })
  it('bez sesji nie ma czego kończyć', () => {
    expect(sessionMismatch({ status: 'signed-out' }, { address: undefined, chainId: 1 }, 31337)).toBeNull()
  })
})

describe('deploymentMismatch', () => {
  it('ta sama sieć i kontrakt', () => {
    expect(deploymentMismatch(context, 31337, context.verifyingContract.toLowerCase())).toBeNull()
  })
  it('backend na innej sieci', () => {
    expect(deploymentMismatch({ ...context, chainId: 80002 }, 31337, context.verifyingContract)).toMatch(/80002/)
  })
  it('backend z innym kontraktem', () => {
    expect(deploymentMismatch(context, 31337, '0x0000000000000000000000000000000000000001')).toMatch(/kontraktu/)
  })
})
