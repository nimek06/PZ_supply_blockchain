// Rozpoznanie odmowy w portfelu (EIP-1193, kod 4001). Viem i wagmi owijają błąd, więc idziemy po `cause`.
export function isUserRejection(error: unknown): boolean {
  let current: unknown = error
  for (let depth = 0; current && depth < 10; depth++) {
    if (typeof current !== 'object') return false
    const { code, name, cause } = current as { code?: unknown; name?: unknown; cause?: unknown }
    if (code === 4001 || name === 'UserRejectedRequestError') return true
    current = cause
  }
  return false
}
