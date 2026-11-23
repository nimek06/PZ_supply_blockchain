import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { BatchSummary } from './BatchSummary'
import { useBatchByNumber } from './batchQueries'
import type { DataSource } from './dataSource'
import { QueryError } from './QueryError'

// Osobne stany: brak numeru, ładowanie, błąd, dane. Nic nie udaje pustego wyniku.
export function BatchLookup({ source, batchNumber, showQr }: { source: DataSource; batchNumber: string | null; showQr?: boolean }) {
  const query = useBatchByNumber(source, batchNumber)

  if (batchNumber === null) {
    return (
      <Card>
        <p className="text-sm text-stone-500">Wpisz numer partii albo zeskanuj kod, żeby zobaczyć jej stan.</p>
      </Card>
    )
  }
  if (query.isPending) {
    return (
      <Card>
        <p className="text-sm text-stone-500" role="status">
          Wczytuję partię {batchNumber}…
        </p>
      </Card>
    )
  }
  if (query.isError) return <QueryError error={query.error} what={`partia ${batchNumber}`} />
  return (
    <div className="grid gap-2">
      <BatchSummary batch={query.data} source={source} updatedAt={query.dataUpdatedAt} showQr={showQr} />
      <div className="flex justify-end">
        <Button variant="ghost" onClick={() => void query.refetch()} disabled={query.isFetching}>
          {query.isFetching ? 'Odświeżam…' : 'Odśwież'}
        </Button>
      </div>
    </div>
  )
}
