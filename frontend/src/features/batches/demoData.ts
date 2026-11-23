import { ApiError } from '../../lib/api/client'
import type { BatchResponse, CommandStatus } from '../../lib/api/types'
import { batchIdOf } from '../../lib/blockchain/typedData'

// DANE DEMONSTRACYJNE. Kształt zgodny z OpenAPI, ale nic tu nie pochodzi z blockchaina.
// UI zawsze oznacza je etykietą "dane demo". B-2026-0007 to partia z shared/fixtures/report-valid-*.

const freshProjection = { lastIndexedBlock: 42, requiredConfirmations: 1, stale: false }

function batch(batchNumber: string, nextStage: number | null, stale = false): BatchResponse {
  return {
    batchId: batchIdOf(batchNumber),
    batchNumber,
    completed: nextStage === null,
    nextStage,
    projection: stale ? { lastIndexedBlock: 17, requiredConfirmations: 1, stale: true } : freshProjection,
  }
}

const BATCHES: Record<string, BatchResponse> = {
  'B-2026-0007': batch('B-2026-0007', 2),
  'B-2026-0008': batch('B-2026-0008', null),
  'B-2026-0009': batch('B-2026-0009', 1, true),
}

export const DEMO_BATCH_NUMBERS = {
  inProgress: 'B-2026-0007',
  completed: 'B-2026-0008',
  stale: 'B-2026-0009',
  notFound: 'B-2026-0404',
  networkError: 'B-2026-0503',
} as const

const REPORT_HASH = '0x388322c4e5f9429a457ab80640548381b4cb000d14e933561f40db7f2b017ffe'
const CID = 'bafkreib4pqtikzdjlj4zigobmd63lig7u6oqxvbvqbk6qdudkjeubguuu4'
const TX = '0x11d36da66eed8208dae918794ca8226c072832865d667e13a3f4fead2f6dc0fb'

function stageCommand(commandId: string, fields: Partial<CommandStatus>): CommandStatus {
  return {
    commandId,
    type: 'STAGE',
    status: 'ACCEPTED',
    outcome: null,
    batchId: batchIdOf('B-2026-0007'),
    batchNumber: 'B-2026-0007',
    stageId: 2,
    reportHash: REPORT_HASH,
    cid: null,
    txHash: null,
    failureCode: null,
    ...fields,
  }
}

export const DEMO_COMMANDS: Record<string, CommandStatus> = {
  '00000000-0000-4000-8000-000000000001': stageCommand('00000000-0000-4000-8000-000000000001', { status: 'ACCEPTED' }),
  '00000000-0000-4000-8000-000000000002': stageCommand('00000000-0000-4000-8000-000000000002', { status: 'WAITING_FOR_QA', cid: CID }),
  '00000000-0000-4000-8000-000000000003': stageCommand('00000000-0000-4000-8000-000000000003', { status: 'SUBMITTED', cid: CID, txHash: TX }),
  '00000000-0000-4000-8000-000000000004': stageCommand('00000000-0000-4000-8000-000000000004', {
    status: 'CONFIRMED', outcome: 'ACCEPTED', cid: CID, txHash: TX,
  }),
  '00000000-0000-4000-8000-000000000005': stageCommand('00000000-0000-4000-8000-000000000005', {
    status: 'CONFIRMED', outcome: 'REJECTED', cid: CID, txHash: TX,
  }),
  '00000000-0000-4000-8000-000000000006': stageCommand('00000000-0000-4000-8000-000000000006', {
    status: 'FAILED', cid: CID, failureCode: 'STATION_NOT_ACTIVE',
  }),
}

const DEMO_DELAY_MS = 400

function later<T>(produce: () => T, signal?: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      try {
        resolve(produce())
      } catch (error) {
        reject(error)
      }
    }, DEMO_DELAY_MS)
    signal?.addEventListener('abort', () => {
      clearTimeout(timer)
      reject(signal.reason)
    })
  })
}

export function demoBatchByNumber(batchNumber: string, signal?: AbortSignal): Promise<BatchResponse> {
  return later(() => {
    if (batchNumber === DEMO_BATCH_NUMBERS.networkError) {
      throw new ApiError('Brak połączenia z backendem', null, null, null)
    }
    const found = BATCHES[batchNumber]
    if (!found) throw new ApiError(`Nie ma partii ${batchNumber}`, 404, 'NOT_FOUND', 'demo')
    return found
  }, signal)
}

export function demoCommand(commandId: string, signal?: AbortSignal): Promise<CommandStatus> {
  return later(() => {
    const found = DEMO_COMMANDS[commandId]
    if (!found) throw new ApiError('Nie ma takiego polecenia', 404, 'NOT_FOUND', 'demo')
    return found
  }, signal)
}
