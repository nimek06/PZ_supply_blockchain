import { useState, type FormEvent } from 'react'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { BatchLookup } from '../batches/BatchLookup'
import { BatchNumberForm } from '../batches/BatchNumberForm'
import { CommandLookup } from '../batches/CommandLookup'
import { useDataSource } from '../batches/dataSource'
import { DataSourceToggle } from '../batches/DemoNotice'
import { DEMO_BATCH_NUMBERS, DEMO_COMMANDS } from '../batches/demoData'
import { CreateBatchPanel } from './CreateBatchPanel'

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function OperatorPage() {
  const [source, setSource] = useDataSource()
  const [batchNumber, setBatchNumber] = useState<string | null>(null)
  const [commandInput, setCommandInput] = useState('')
  const [commandId, setCommandId] = useState<string | null>(null)
  const [commandInvalid, setCommandInvalid] = useState(false)

  const showCommand = (event: FormEvent) => {
    event.preventDefault()
    const value = commandInput.trim().toLowerCase()
    setCommandInvalid(!UUID.test(value))
    if (UUID.test(value)) setCommandId(value)
  }

  return (
    <div className="grid gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold tracking-tight">Panel operatora</h1>
          <p className="mt-1 text-sm text-stone-600">Tworzenie partii i podgląd ich stanu.</p>
        </div>
        <DataSourceToggle source={source} onChange={setSource} />
      </div>

      <CreateBatchPanel source={source} />

      <Card title="Znajdź partię">
        <BatchNumberForm submitLabel="Pokaż partię" onBatch={setBatchNumber} />
      </Card>
      {batchNumber && <BatchLookup source={source} batchNumber={batchNumber} showQr />}

      <Card title="Status polecenia">
        <form onSubmit={showCommand} className="flex flex-col gap-2 sm:flex-row sm:items-end">
          <label className="flex grow flex-col gap-1 text-sm">
            ID polecenia (z odpowiedzi 202)
            <input
              value={commandInput}
              onChange={(event) => setCommandInput(event.target.value)}
              placeholder="00000000-0000-4000-8000-000000000000"
              autoComplete="off"
              aria-invalid={commandInvalid}
              className="font-mono"
            />
          </label>
          <Button type="submit">Sprawdź status</Button>
        </form>
        {commandInvalid && <p className="mt-2 text-xs text-rose-700">ID polecenia to UUID.</p>}
      </Card>
      <CommandLookup source={source} commandId={commandId} />

      {source === 'demo' && (
        <details className="text-sm text-stone-600">
          <summary className="cursor-pointer">Przykłady w danych demo</summary>
          <ul className="mt-2 grid gap-1 pl-4 font-mono text-xs">
            {Object.values(DEMO_BATCH_NUMBERS).map((value) => (
              <li key={value}>{value}</li>
            ))}
            {Object.values(DEMO_COMMANDS).map((command) => (
              <li key={command.commandId}>
                {command.commandId} · {command.status}
                {command.outcome ? ` · ${command.outcome}` : ''}
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
