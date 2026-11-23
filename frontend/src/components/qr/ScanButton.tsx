import { lazy, Suspense, useState } from 'react'
import { Button } from '../ui/Button'

// Skaner (z biblioteką wasm) ładuje się dopiero po kliknięciu, żeby nie spowalniać startu strony.
const BatchQrScanner = lazy(() => import('./BatchQrScanner'))

export function ScanButton({ onBatch }: { onBatch: (batchNumber: string) => void }) {
  const [open, setOpen] = useState(false)
  if (!open) {
    return (
      <div>
        <Button variant="secondary" onClick={() => setOpen(true)}>
          Skanuj kod QR
        </Button>
      </div>
    )
  }
  return (
    <div className="w-full">
      <Suspense fallback={<p className="text-sm text-slate-500">Uruchamiam skaner…</p>}>
        <BatchQrScanner
          onBatch={(batchNumber) => {
            setOpen(false)
            onBatch(batchNumber)
          }}
          onClose={() => setOpen(false)}
        />
      </Suspense>
    </div>
  )
}
