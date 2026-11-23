// Zeskanowany kod może być samym numerem partii albo adresem paszportu tej aplikacji.
// Inny adres odrzucamy, żeby skaner nigdy nie przenosił na obcą stronę.

const BATCH = /^B-[0-9]{4}-[0-9]{4}$/

export type ScanResult = { ok: true; batchNumber: string } | { ok: false; reason: string }

export function parseBatchQr(text: string, appOrigin: string): ScanResult {
  const value = text.trim()
  if (BATCH.test(value.toUpperCase())) return { ok: true, batchNumber: value.toUpperCase() }

  let url: URL
  try {
    url = new URL(value)
  } catch {
    return { ok: false, reason: 'To nie jest kod partii' }
  }
  if (url.origin !== new URL(appOrigin).origin) {
    return { ok: false, reason: 'Kod prowadzi do innej strony, nie otwieram go' }
  }
  const match = /^\/passport\/(B-[0-9]{4}-[0-9]{4})\/?$/i.exec(url.pathname)
  if (!match?.[1]) return { ok: false, reason: 'Adres nie wskazuje paszportu partii' }
  return { ok: true, batchNumber: match[1].toUpperCase() }
}

/** Publiczny adres paszportu, który trafia do kodu QR. Bez podpisów i tokenów. */
export function passportUrl(batchNumber: string, appOrigin: string): string {
  return new URL(`/passport/${batchNumber}`, appOrigin).toString()
}
