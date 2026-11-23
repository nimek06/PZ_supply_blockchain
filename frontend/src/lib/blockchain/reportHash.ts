import canonicalize from 'canonicalize'
import { keccak256, stringToBytes, type Hex } from 'viem'

// reportHash = keccak256(UTF-8(JCS(raport))), RFC 8785, tak jak w symulatorze i backendzie.

export function canonicalReport(report: unknown): string {
  const text = canonicalize(report)
  if (text === undefined) {
    throw new Error('raportu nie da się zapisać jako JSON')
  }
  return text
}

export function reportHash(report: unknown): Hex {
  return keccak256(stringToBytes(canonicalReport(report)))
}
