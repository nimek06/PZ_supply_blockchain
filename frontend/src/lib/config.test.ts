import { describe, expect, it } from 'vitest'
import { readConfig } from './config'

describe('readConfig', () => {
  it('domyślnie lokalny Anvil przez proxy serwera, RPC jako pełny adres strony', () => {
    expect(readConfig({}, 'https://192.168.0.9:5173')).toEqual({
      chainId: 31337,
      rpcUrl: 'https://192.168.0.9:5173/rpc',
      contractAddress: '0x5FbDB2315678afecb367f032d93F642f64180aa3',
      apiBaseUrl: '/api/v1',
      ipfsGatewayUrl: '/ipfs',
    })
  })
  it('pełny adres RPC z konfiguracji zostaje bez zmian', () => {
    expect(readConfig({ VITE_RPC_URL: 'http://127.0.0.1:8545' }, 'https://x').rpcUrl).toBe('http://127.0.0.1:8545')
  })
  it('odrzuca złą sieć i zły adres', () => {
    expect(() => readConfig({ VITE_CHAIN_ID: 'abc' })).toThrow(/VITE_CHAIN_ID/)
    expect(() => readConfig({ VITE_CONTRACT_ADDRESS: '0x123' })).toThrow(/VITE_CONTRACT_ADDRESS/)
  })
})
