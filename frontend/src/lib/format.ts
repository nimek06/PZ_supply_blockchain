/** 0x90F7…b906 - skrócony adres do nagłówka, pełny jest w title. */
export function shortAddress(address: string): string {
  return `${address.slice(0, 6)}…${address.slice(-4)}`
}
