import { describe, expect, it } from 'vitest'
import { parseBatchQr, passportUrl } from './parseBatchQr'

const APP = 'https://192.168.0.9:5173'

describe('parseBatchQr', () => {
  it('sam numer partii', () => {
    expect(parseBatchQr(' b-2026-0007 ', APP)).toEqual({ ok: true, batchNumber: 'B-2026-0007' })
  })
  it('adres paszportu tej aplikacji', () => {
    expect(parseBatchQr(`${APP}/passport/B-2026-0007`, APP)).toEqual({ ok: true, batchNumber: 'B-2026-0007' })
  })
  it('obcy adres jest odrzucany, nawet ze ścieżką paszportu', () => {
    expect(parseBatchQr('https://evil.example/passport/B-2026-0007', APP)).toMatchObject({ ok: false })
    expect(parseBatchQr('http://192.168.0.9:5173/passport/B-2026-0007', APP)).toMatchObject({ ok: false })
  })
  it('inne treści są odrzucane', () => {
    expect(parseBatchQr(`${APP}/operator`, APP)).toMatchObject({ ok: false })
    expect(parseBatchQr('javascript:alert(1)', APP)).toMatchObject({ ok: false })
    expect(parseBatchQr('B-2026-7', APP)).toMatchObject({ ok: false })
    expect(parseBatchQr('', APP)).toMatchObject({ ok: false })
  })
  it('kod generowany przez aplikację da się odczytać z powrotem', () => {
    expect(parseBatchQr(passportUrl('B-2026-0001', APP), APP)).toEqual({ ok: true, batchNumber: 'B-2026-0001' })
  })
})
