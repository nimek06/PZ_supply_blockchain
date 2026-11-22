import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { useSession } from '../../app/session'
import { createBatch } from '../../lib/api/batchesApi'
import { toApiError } from '../../lib/api/client'
import type { CommandAccepted } from '../../lib/api/types'
import { BatchLookup } from '../batches/BatchLookup'
import { useCommandStatus } from '../batches/batchQueries'
import { CommandStatusCard } from '../batches/CommandStatusCard'
import type { DataSource } from '../batches/dataSource'
import { QueryError } from '../batches/QueryError'
import { clearIntent, loadIntent, newIntent, saveIntent, type CreateBatchIntent } from './createBatchIntent'

export function CreateBatchPanel({ source }: { source: DataSource }) {
  const { state } = useSession()

  if (source === 'demo') {
    return (
      <Card title="Nowa partia">
        <p className="text-sm text-slate-600">Tworzenie partii działa tylko na prawdziwym API. Wyłącz dane demo.</p>
      </Card>
    )
  }
  if (state.status !== 'signed-in') {
    return (
      <Card title="Nowa partia">
        <p className="text-sm text-slate-600">Zaloguj się portfelem, żeby utworzyć partię.</p>
      </Card>
    )
  }
  if (!state.context.roles.includes('OPERATOR_ROLE')) {
    return (
      <Card title="Nowa partia">
        <p className="text-sm text-slate-600">To konto nie ma roli operatora (OPERATOR_ROLE), więc nie może tworzyć partii.</p>
      </Card>
    )
  }
  // key: po zmianie konta panel startuje od zera z zamiarem tego konta
  return <CreateBatchFlow key={state.context.address} address={state.context.address} />
}

function CreateBatchFlow({ address }: { address: string }) {
  const [intent, setIntent] = useState<CreateBatchIntent | null>(() => loadIntent(address))

  const update = (next: CreateBatchIntent | null) => {
    if (next) saveIntent(address, next)
    else clearIntent(address)
    setIntent(next)
  }

  const mutation = useMutation({
    mutationFn: (current: CreateBatchIntent) => createBatch(current.idempotencyKey),
    onSuccess: (accepted: CommandAccepted, current) =>
      update({ ...current, commandId: accepted.commandId, batchNumber: accepted.batchNumber }),
  })

  const start = () => {
    const created = newIntent()
    update(created)
    mutation.mutate(created)
  }

  const reset = () => {
    mutation.reset()
    update(null)
  }

  if (!intent) {
    return (
      <Card title="Nowa partia">
        <p className="mb-3 text-sm text-slate-600">Numer partii nada backend. Partia powstanie na blockchainie dopiero po potwierdzeniu transakcji.</p>
        <Button onClick={start}>Utwórz partię</Button>
      </Card>
    )
  }

  if (!intent.commandId) {
    const error = mutation.error ? toApiError(mutation.error) : null
    const transient = error !== null && (error.isNetworkError || (error.status ?? 0) >= 500)
    return (
      <Card title="Nowa partia">
        {mutation.isPending && (
          <p className="text-sm text-slate-600" role="status">
            Wysyłam polecenie utworzenia partii…
          </p>
        )}
        {!mutation.isPending && !error && (
          <Alert tone="warning" title="Poprzednie zgłoszenie nie ma jeszcze odpowiedzi">
            Wyślij je ponownie. Ten sam klucz sprawi, że backend nie utworzy drugiej partii.
          </Alert>
        )}
        {error && <QueryError error={error} what="utworzenie partii" />}
        {!mutation.isPending && (
          <div className="mt-3 flex flex-wrap gap-2">
            {(transient || !error) && <Button onClick={() => mutation.mutate(intent)}>Ponów to samo zgłoszenie</Button>}
            <Button variant="secondary" onClick={reset}>
              {transient || !error ? 'Porzuć' : 'Zacznij od nowa'}
            </Button>
          </div>
        )}
      </Card>
    )
  }

  return <CreatedBatch intent={intent} onNew={reset} />
}

function CreatedBatch({ intent, onNew }: { intent: CreateBatchIntent; onNew: () => void }) {
  const command = useCommandStatus('api', intent.commandId ?? null)
  const status = command.data?.status
  const batchNumber = command.data?.batchNumber ?? intent.batchNumber ?? null

  return (
    <div className="grid gap-4">
      {command.isPending && (
        <Card title="Nowa partia">
          <p className="text-sm text-slate-600" role="status">
            Wczytuję status polecenia…
          </p>
        </Card>
      )}
      {command.isError && <QueryError error={command.error} what="polecenie utworzenia partii" />}
      {command.data && <CommandStatusCard command={command.data} source="api" />}
      {status === 'CONFIRMED' && batchNumber && <BatchLookup source="api" batchNumber={batchNumber} />}
      {(status === 'CONFIRMED' || status === 'FAILED' || command.isError) && (
        <div>
          <Button variant="secondary" onClick={onNew}>
            Utwórz kolejną partię
          </Button>
        </div>
      )}
    </div>
  )
}
