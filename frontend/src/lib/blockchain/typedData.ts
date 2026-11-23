import { keccak256, stringToBytes, type Address, type Hex } from 'viem'

// Typy EIP-712 z protokołu. Muszą dać te same skróty co Python, Java i Solidity,
// pilnuje tego tests/signatureVector.test.ts.

export const DOMAIN_NAME = 'SupplyChain'
export const DOMAIN_VERSION = '1'

export function supplyChainDomain(chainId: number, verifyingContract: Address) {
  return { name: DOMAIN_NAME, version: DOMAIN_VERSION, chainId, verifyingContract } as const
}

export const stageCompletionTypes = {
  StageCompletion: [
    { name: 'batchId', type: 'bytes32' },
    { name: 'stageId', type: 'uint32' },
    { name: 'reportHash', type: 'bytes32' },
    { name: 'qaPassed', type: 'bool' },
    { name: 'deadline', type: 'uint256' },
  ],
} as const

export const stageApprovalTypes = {
  StageApproval: [
    { name: 'batchId', type: 'bytes32' },
    { name: 'stageId', type: 'uint32' },
    { name: 'reportHash', type: 'bytes32' },
    { name: 'deadline', type: 'uint256' },
  ],
} as const

/** `batchId = keccak256(utf8(batchNumber))` (DEC-12). */
export function batchIdOf(batchNumber: string): Hex {
  return keccak256(stringToBytes(batchNumber))
}
