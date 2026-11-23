import { useNavigate } from 'react-router'
import { Card } from '../components/ui/Card'
import { BatchNumberForm } from '../features/batches/BatchNumberForm'

export function HomePage() {
  const navigate = useNavigate()
  return (
    <div className="grid gap-6">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">Paszport partii</h1>
        <p className="mt-1 text-sm text-stone-600">Sprawdź, przez które etapy produkcji przeszła partia rowerów. Nie potrzebujesz portfela.</p>
      </div>
      <Card>
        <BatchNumberForm submitLabel="Pokaż partię" onBatch={(batchNumber) => void navigate(`/passport/${batchNumber}`)} />
      </Card>
    </div>
  )
}
