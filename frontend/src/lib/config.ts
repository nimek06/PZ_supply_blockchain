import { getAddress, isAddress, type Address } from 'viem'

// Publiczna konfiguracja z VITE_*. Wszystko tutaj widzi przeglądarka, więc żadnych sekretów.
export interface AppConfig {
  chainId: number
  rpcUrl: string
  contractAddress: Address
  apiBaseUrl: string
  ipfsGatewayUrl: string
}

// Domyślnie wszystko przez proxy serwera Vite, więc działa tak samo na komputerze i na telefonie.
const DEFAULTS = {
  chainId: '31337',
  rpcUrl: '/rpc',
  contractAddress: '0x5FbDB2315678afecb367f032d93F642f64180aa3',
  apiBaseUrl: '/api/v1',
  ipfsGatewayUrl: '/ipfs',
}

type Env = Record<string, string | undefined>

/** Portfel i viem potrzebują pełnego adresu, więc ścieżkę `/rpc` uzupełniamy adresem strony. */
function absolute(url: string, origin: string | undefined): string {
  return url.startsWith('/') && origin ? new URL(url, origin).toString().replace(/\/$/, '') : url
}

export function readConfig(env: Env, origin?: string): AppConfig {
  const chainId = Number(env.VITE_CHAIN_ID || DEFAULTS.chainId)
  if (!Number.isSafeInteger(chainId) || chainId <= 0) {
    throw new Error(`VITE_CHAIN_ID musi być dodatnią liczbą, jest: ${env.VITE_CHAIN_ID}`)
  }
  const contract = env.VITE_CONTRACT_ADDRESS || DEFAULTS.contractAddress
  if (!isAddress(contract)) {
    throw new Error(`VITE_CONTRACT_ADDRESS nie jest adresem: ${contract}`)
  }
  return {
    chainId,
    rpcUrl: absolute(env.VITE_RPC_URL || DEFAULTS.rpcUrl, origin),
    contractAddress: getAddress(contract),
    apiBaseUrl: env.VITE_API_BASE_URL || DEFAULTS.apiBaseUrl,
    ipfsGatewayUrl: env.VITE_IPFS_GATEWAY_URL || DEFAULTS.ipfsGatewayUrl,
  }
}

export const config = readConfig(import.meta.env, typeof window === 'undefined' ? undefined : window.location.origin)
