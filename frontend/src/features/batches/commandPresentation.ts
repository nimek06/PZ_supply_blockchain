import type { Tone } from '../../components/ui/tone'
import type { CommandState, CommandStatus } from '../../lib/api/types'

export interface CommandView {
  label: string
  description: string
  tone: Tone
  terminal: boolean
}

// Tylko CONFIRMED z łańcucha oznacza wykonanie. 202 i SUBMITTED to wciąż oczekiwanie.
const STATES: Record<CommandState, Omit<CommandView, 'terminal'>> = {
  ACCEPTED: { label: 'Przyjęte', description: 'Backend zapisał polecenie, jeszcze nic nie trafiło na blockchain', tone: 'pending' },
  PINNING: { label: 'Zapis raportu', description: 'Raport jest zapisywany w IPFS', tone: 'pending' },
  WAITING_FOR_QA: { label: 'Czeka na operatora QA', description: 'Kontrola wymaga drugiego podpisu operatora zmiany', tone: 'warning' },
  READY_TO_SUBMIT: { label: 'Gotowe do wysłania', description: 'Czeka w kolejce relayera', tone: 'pending' },
  SUBMITTED: { label: 'Wysłane', description: 'Transakcja wysłana, czeka na potwierdzenie w bloku', tone: 'pending' },
  CONFIRMED: { label: 'Potwierdzone', description: 'Zdarzenie potwierdzone na blockchainie', tone: 'success' },
  FAILED: { label: 'Nieudane', description: 'Polecenie nie zostało wykonane', tone: 'danger' },
}

export function isTerminal(state: CommandState): boolean {
  return state === 'CONFIRMED' || state === 'FAILED'
}

export function describeCommand(command: Pick<CommandStatus, 'status' | 'outcome' | 'type'>): CommandView {
  const base = STATES[command.status]
  const view = { ...base, terminal: isTerminal(command.status) }
  if (command.status === 'CONFIRMED' && command.outcome === 'REJECTED') {
    return {
      ...view,
      label: 'Odrzucone',
      description: 'Stacja zgłosiła wynik negatywny (StageRejected), partia nie przeszła dalej',
      tone: 'danger',
    }
  }
  if (command.status === 'CONFIRMED' && command.type === 'CREATE_BATCH') {
    return { ...view, description: 'Partia istnieje na blockchainie' }
  }
  return view
}
