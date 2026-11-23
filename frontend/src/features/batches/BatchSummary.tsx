import { BatchQrCode } from '../../components/qr/BatchQrCode'
import { Alert } from '../../components/ui/Alert'
import { Badge } from '../../components/ui/Badge'
import { Card } from '../../components/ui/Card'
import { Field, Fields } from '../../components/ui/Field'
import { Mono } from '../../components/ui/Mono'
import type { BatchResponse } from '../../lib/api/types'
import type { DataSource } from './dataSource'
import { DemoBadge } from './DemoNotice'
import { StageProgress } from './StageProgress'
import { stageName } from './stages'

interface Props {
  batch: BatchResponse
  source: DataSource
  updatedAt?: number
  showQr?: boolean
}

export function BatchSummary({ batch, source, updatedAt, showQr = false }: Props) {
  const stale = batch.projection.stale === true
  return (
    <Card
      title={`Partia ${batch.batchNumber}`}
      actions={
        <div className="flex gap-2">
          <DemoBadge source={source} />
          {batch.completed ? <Badge tone="success">ukończona</Badge> : <Badge tone="pending">w produkcji</Badge>}
        </div>
      }
    >
      <div className="grid gap-4">
        {stale && (
          <Alert tone="warning" title="Dane mogą być nieaktualne">
            Indexer nie nadąża albo RPC jest niedostępne. Ostatni przetworzony blok: {batch.projection.lastIndexedBlock}.
          </Alert>
        )}
        <StageProgress batch={batch} />
        {!batch.completed && batch.nextStage !== null && (
          <p className="text-sm text-stone-600">
            Czeka na raport stacji: {stageName(batch.nextStage).toLowerCase()} (etap {batch.nextStage}).
          </p>
        )}
        <div className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
          <Fields>
            <Field label="batchId">
              <Mono>{batch.batchId}</Mono>
            </Field>
            <Field label="Projekcja">
              blok {batch.projection.lastIndexedBlock}, potwierdzenia: {batch.projection.requiredConfirmations}
              {stale ? ', nieaktualna' : ''}
            </Field>
            {updatedAt !== undefined && updatedAt > 0 && (
              <Field label="Odczytano">{new Date(updatedAt).toLocaleTimeString('pl-PL')}</Field>
            )}
          </Fields>
          {showQr && source === 'api' && <BatchQrCode batchNumber={batch.batchNumber} appOrigin={window.location.origin} />}
        </div>
      </div>
    </Card>
  )
}
