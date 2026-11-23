import { Scanner, type IDetectedBarcode, type IScannerError } from '@yudiel/react-qr-scanner'
import { useState } from 'react'
import { Alert } from '../ui/Alert'
import { Button } from '../ui/Button'
import { parseBatchQr } from './parseBatchQr'
import './zxing'

const CAMERA_ERRORS: Partial<Record<IScannerError['kind'], string>> = {
  'permission-denied': 'Brak zgody na kamerę. Zezwól na nią w ustawieniach przeglądarki albo wpisz numer ręcznie.',
  'no-camera': 'Nie znaleziono kamery. Wpisz numer partii ręcznie.',
  'in-use': 'Kamera jest zajęta przez inną aplikację.',
  'insecure-context': 'Kamera działa tylko przez HTTPS. Uruchom aplikację przez npm run dev:https.',
  unsupported: 'Ta przeglądarka nie obsługuje kamery w tym trybie.',
  overconstrained: 'Kamera nie obsługuje wymaganych ustawień obrazu. Spróbuj innej kamery albo wpisz numer ręcznie.',
}

interface Props {
  onBatch: (batchNumber: string) => void
  onClose: () => void
}

// Skaner jest zamontowany tylko, gdy jest otwarty, więc odmontowanie zatrzymuje kamerę.
export default function BatchQrScanner({ onBatch, onClose }: Props) {
  const [problem, setProblem] = useState<string | null>(null)
  const [cameraError, setCameraError] = useState<string | null>(null)

  const handleScan = (codes: IDetectedBarcode[]) => {
    const text = codes[0]?.rawValue
    if (text === undefined) return
    const result = parseBatchQr(text, window.location.origin)
    if (result.ok) onBatch(result.batchNumber)
    else setProblem(result.reason)
  }

  return (
    <div className="grid gap-3">
      {cameraError ? (
        <Alert tone="warning" title="Kamera niedostępna">
          {cameraError}
        </Alert>
      ) : (
        <div className="mx-auto w-full max-w-xs overflow-hidden rounded-lg border border-slate-200">
          <Scanner
            onScan={handleScan}
            onError={(error) => setCameraError(CAMERA_ERRORS[error.kind] ?? `Błąd kamery (${error.kind})${error.message ? `: ${error.message}` : ''}`)}
            formats={['qr_code']}
            constraints={{ facingMode: 'environment' }}
            components={{ finder: true, torch: true }}
            sound={false}
          />
        </div>
      )}
      {problem && <Alert tone="warning" title={problem} />}
      <Button variant="secondary" onClick={onClose}>
        Zamknij skaner
      </Button>
    </div>
  )
}
