import type { StageCode } from '../../lib/api/types'

// Etapy e-bike z docs/project/product-scenario.md. Kody są stałe, nazwy tylko do wyświetlania.
export const STAGES: readonly { id: number; code: StageCode; name: string }[] = [
  { id: 0, code: 'PREPARATION', name: 'Przygotowanie' },
  { id: 1, code: 'ASSEMBLY', name: 'Montaż' },
  { id: 2, code: 'QUALITY_CONTROL', name: 'Kontrola jakości' },
  { id: 3, code: 'PACKAGING', name: 'Pakowanie' },
]

export function stageName(stageId: number): string {
  return STAGES[stageId]?.name ?? `Etap ${stageId}`
}

export const BATCH_NUMBER_PATTERN = /^B-[0-9]{4}-[0-9]{4}$/

export function normalizeBatchNumber(input: string): string | null {
  const value = input.trim().toUpperCase()
  return BATCH_NUMBER_PATTERN.test(value) ? value : null
}
