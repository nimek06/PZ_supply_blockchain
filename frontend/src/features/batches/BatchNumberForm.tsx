import { useId, useState, type FormEvent } from 'react'
import { ScanButton } from '../../components/qr/ScanButton'
import { Button } from '../../components/ui/Button'
import { normalizeBatchNumber } from './stages'

// Numer partii wpisany ręcznie albo zeskanowany z QR. Do rodzica trafia tylko poprawny numer.
export function BatchNumberForm({ submitLabel, onBatch }: { submitLabel: string; onBatch: (batchNumber: string) => void }) {
  const id = useId()
  const [value, setValue] = useState('')
  const [invalid, setInvalid] = useState(false)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const batchNumber = normalizeBatchNumber(value)
    setInvalid(batchNumber === null)
    if (batchNumber) onBatch(batchNumber)
  }

  return (
    <div className="grid gap-3">
      <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row sm:items-end">
        <label htmlFor={id} className="flex grow flex-col gap-1 text-sm">
          Numer partii
          <input
            id={id}
            value={value}
            onChange={(event) => {
              setValue(event.target.value)
              setInvalid(false)
            }}
            placeholder="B-2026-0001"
            autoComplete="off"
            aria-invalid={invalid}
            aria-describedby={`${id}-hint`}
            className="font-mono"
          />
        </label>
        <Button type="submit">{submitLabel}</Button>
      </form>
      <p id={`${id}-hint`} className={`text-xs ${invalid ? 'text-rose-700' : 'text-stone-500'}`}>
        {invalid ? 'Numer partii ma format B-RRRR-NNNN.' : 'Format B-RRRR-NNNN, np. B-2026-0001.'}
      </p>
      <ScanButton
        onBatch={(batchNumber) => {
          setValue(batchNumber)
          setInvalid(false)
          onBatch(batchNumber)
        }}
      />
    </div>
  )
}
