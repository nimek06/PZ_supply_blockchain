import type { BatchResponse } from '../../lib/api/types'
import { STAGES } from './stages'

type StepState = 'done' | 'next' | 'todo'

function stepState(batch: Pick<BatchResponse, 'completed' | 'nextStage'>, stageId: number): StepState {
  if (batch.completed) return 'done'
  if (batch.nextStage === null) return 'todo'
  if (stageId < batch.nextStage) return 'done'
  return stageId === batch.nextStage ? 'next' : 'todo'
}

const LABELS: Record<StepState, string> = { done: 'potwierdzony', next: 'następny', todo: 'oczekuje' }

// Postęp partii z projekcji backendu. "Potwierdzony" tylko dla etapów przed nextStage.
export function StageProgress({ batch }: { batch: Pick<BatchResponse, 'completed' | 'nextStage'> }) {
  return (
    <ol className="grid grid-cols-2 gap-px overflow-hidden rounded-md border border-stone-200 bg-stone-200 sm:grid-cols-4" aria-label="Etapy partii">
      {STAGES.map((stage) => {
        const state = stepState(batch, stage.id)
        return (
          <li key={stage.id} className={`px-3 py-2.5 ${state === 'next' ? 'bg-stone-50' : 'bg-white'}`} aria-current={state === 'next' ? 'step' : undefined}>
            <span className="flex items-center gap-2 text-sm font-medium">
              <span
                aria-hidden
                className={`flex size-5 items-center justify-center rounded-full text-[11px] ${
                  state === 'done' ? 'bg-stone-900 text-white' : state === 'next' ? 'border-2 border-stone-900' : 'border border-stone-300 text-stone-400'
                }`}
              >
                {state === 'done' ? '✓' : stage.id}
              </span>
              {stage.name}
            </span>
            <span className="mt-0.5 block pl-7 text-xs text-stone-500">{LABELS[state]}</span>
          </li>
        )
      })}
    </ol>
  )
}
