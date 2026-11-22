// Zamiar utworzenia partii: jeden Idempotency-Key na jedno kliknięcie "Utwórz partię".
// Ponowienie po błędzie sieci wysyła ten sam klucz, więc backend nie utworzy drugiej partii.
// Trzymamy go w sessionStorage (to nie jest sekret), żeby po odświeżeniu dało się odzyskać status.

export interface CreateBatchIntent {
  idempotencyKey: string
  createdAt: number
  commandId?: string
  batchNumber?: string
}

const PREFIX = 'create-batch-intent:'

function storageKey(address: string): string {
  return PREFIX + address.toLowerCase()
}

function isIntent(value: unknown): value is CreateBatchIntent {
  if (typeof value !== 'object' || value === null) return false
  const v = value as Record<string, unknown>
  return typeof v.idempotencyKey === 'string' && typeof v.createdAt === 'number'
}

export function loadIntent(address: string, storage: Storage | undefined = safeStorage()): CreateBatchIntent | null {
  try {
    const raw = storage?.getItem(storageKey(address))
    if (!raw) return null
    const parsed: unknown = JSON.parse(raw)
    return isIntent(parsed) ? parsed : null
  } catch {
    return null
  }
}

export function saveIntent(address: string, intent: CreateBatchIntent, storage: Storage | undefined = safeStorage()): void {
  try {
    storage?.setItem(storageKey(address), JSON.stringify(intent))
  } catch {
    // brak storage (tryb prywatny) - zamiar żyje tylko w pamięci strony
  }
}

export function clearIntent(address: string, storage: Storage | undefined = safeStorage()): void {
  try {
    storage?.removeItem(storageKey(address))
  } catch {
    // jak wyżej
  }
}

export function newIntent(now = Date.now()): CreateBatchIntent {
  return { idempotencyKey: crypto.randomUUID(), createdAt: now }
}

function safeStorage(): Storage | undefined {
  try {
    return typeof window === 'undefined' ? undefined : window.sessionStorage
  } catch {
    return undefined
  }
}
