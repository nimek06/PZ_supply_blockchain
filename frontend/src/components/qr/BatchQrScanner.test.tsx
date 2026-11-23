import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import BatchQrScanner from './BatchQrScanner'

// Kamera jest podstawiona: komponent dostaje to, co zwróciłaby biblioteka skanera.
const scanner = vi.hoisted(() => ({ value: '', error: null as null | { kind: string; message: string } }))

vi.mock('./zxing', () => ({}))
vi.mock('@yudiel/react-qr-scanner', () => ({
  Scanner: ({ onScan, onError }: { onScan: (codes: { rawValue: string }[]) => void; onError: (e: unknown) => void }) => (
    <button onClick={() => (scanner.error ? onError({ ...scanner.error, cause: null }) : onScan([{ rawValue: scanner.value }]))}>kamera</button>
  ),
}))

describe('BatchQrScanner', () => {
  it('poprawny kod zwraca numer partii', async () => {
    scanner.error = null
    scanner.value = `${window.location.origin}/passport/B-2026-0007`
    const onBatch = vi.fn()
    render(<BatchQrScanner onBatch={onBatch} onClose={() => {}} />)
    await userEvent.click(screen.getByText('kamera'))
    expect(onBatch).toHaveBeenCalledWith('B-2026-0007')
  })

  it('obcy adres nie przenosi nigdzie', async () => {
    scanner.error = null
    scanner.value = 'https://evil.example/passport/B-2026-0007'
    const onBatch = vi.fn()
    render(<BatchQrScanner onBatch={onBatch} onClose={() => {}} />)
    await userEvent.click(screen.getByText('kamera'))
    expect(onBatch).not.toHaveBeenCalled()
    expect(screen.getByText(/innej strony/)).toBeInTheDocument()
  })

  it('odmowa dostępu do kamery ma zrozumiały komunikat', async () => {
    scanner.error = { kind: 'permission-denied', message: 'NotAllowedError' }
    render(<BatchQrScanner onBatch={() => {}} onClose={() => {}} />)
    await userEvent.click(screen.getByText('kamera'))
    expect(screen.getByText(/Brak zgody na kamerę/)).toBeInTheDocument()
  })

  it('bez HTTPS kamera nie ruszy, komunikat mówi dlaczego', async () => {
    scanner.error = { kind: 'insecure-context', message: 'insecure' }
    render(<BatchQrScanner onBatch={() => {}} onClose={() => {}} />)
    await userEvent.click(screen.getByText('kamera'))
    expect(screen.getByText(/tylko przez HTTPS/)).toBeInTheDocument()
  })
})
