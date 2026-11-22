import { Link, useParams } from 'react-router'
import { Alert } from '../../components/ui/Alert'
import { BatchLookup } from '../batches/BatchLookup'
import { useDataSource } from '../batches/dataSource'
import { normalizeBatchNumber } from '../batches/stages'

// Publiczny widok bez portfela. Pełny paszport (historia, raport z IPFS, sprawdzenie hasha) to tydzień 7.
export function PassportPage() {
  const { batchNumber: raw = '' } = useParams()
  const [source] = useDataSource()
  const batchNumber = normalizeBatchNumber(raw)

  if (!batchNumber) {
    return <Alert tone="danger" title="Nieprawidłowy numer partii">Numer ma format B-RRRR-NNNN.</Alert>
  }
  return (
    <div className="grid gap-6">
      <div>
        <Link to="/" className="text-sm text-stone-500 hover:text-stone-900">
          ← Inna partia
        </Link>
        <h1 className="mt-2 text-xl font-semibold tracking-tight">Paszport partii {batchNumber}</h1>
      </div>
      <BatchLookup source={source} batchNumber={batchNumber} showQr />
      <p className="text-xs text-stone-500">Historia etapów i weryfikacja raportów z IPFS pojawią się w pełnym paszporcie.</p>
    </div>
  )
}
