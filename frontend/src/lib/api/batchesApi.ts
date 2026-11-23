import { api } from './client'
import type { BatchResponse, CommandAccepted } from './types'

export async function getBatch(batchId: string, signal?: AbortSignal): Promise<BatchResponse> {
  const { data } = await api.get<BatchResponse>(`/batches/${encodeURIComponent(batchId)}`, { signal })
  return data
}

export async function getBatchByNumber(batchNumber: string, signal?: AbortSignal): Promise<BatchResponse> {
  const { data } = await api.get<BatchResponse>(`/batch-numbers/${encodeURIComponent(batchNumber)}`, { signal })
  return data
}

/** Ten sam `idempotencyKey` dla ponowienia tego samego zamiaru, nowy dla nowej partii. */
export async function createBatch(idempotencyKey: string): Promise<CommandAccepted> {
  const { data } = await api.post<CommandAccepted>('/batches', {}, { headers: { 'Idempotency-Key': idempotencyKey } })
  return data
}
