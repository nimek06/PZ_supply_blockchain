import { useQuery, useQueryClient, type Query } from '@tanstack/react-query'
import { useEffect } from 'react'
import { getBatchByNumber } from '../../lib/api/batchesApi'
import { ApiError } from '../../lib/api/client'
import { getCommand } from '../../lib/api/commandsApi'
import type { CommandStatus } from '../../lib/api/types'
import { config } from '../../lib/config'
import { isTerminal } from './commandPresentation'
import type { DataSource } from './dataSource'
import { demoBatchByNumber, demoCommand } from './demoData'

export const COMMAND_POLL_MS = 2_000

// Klucze zawierają źródło i sieć, żeby dane demo i dane z innej sieci nigdy się nie mieszały.
export const batchKeys = {
  all: ['batch'] as const,
  byNumber: (source: DataSource, batchNumber: string) => ['batch', source, config.chainId, 'number', batchNumber] as const,
}

export const commandKeys = {
  byId: (source: DataSource, commandId: string) => ['command', source, config.chainId, commandId] as const,
}

export function useBatchByNumber(source: DataSource, batchNumber: string | null) {
  return useQuery({
    queryKey: batchKeys.byNumber(source, batchNumber ?? ''),
    queryFn: ({ signal }) =>
      source === 'demo' ? demoBatchByNumber(batchNumber!, signal) : getBatchByNumber(batchNumber!, signal),
    enabled: batchNumber !== null,
  })
}

/** Odpytuje co 2 s, aż polecenie skończy się CONFIRMED albo FAILED. Trwały błąd (401, 404) też kończy polling. */
export function commandRefetchInterval(query: Query<CommandStatus, Error>): number | false {
  const { data, error } = query.state
  if (data && isTerminal(data.status)) return false
  if (error instanceof ApiError && error.status !== null && error.status < 500) return false
  return COMMAND_POLL_MS
}

export function useCommandStatus(source: DataSource, commandId: string | null) {
  const queryClient = useQueryClient()
  const query = useQuery({
    queryKey: commandKeys.byId(source, commandId ?? ''),
    queryFn: ({ signal }) => (source === 'demo' ? demoCommand(commandId!, signal) : getCommand(commandId!, signal)),
    enabled: commandId !== null,
    refetchInterval: commandRefetchInterval,
  })

  // Po potwierdzeniu stan partii się zmienił, więc odświeżamy jej dane.
  const confirmed = query.data?.status === 'CONFIRMED'
  useEffect(() => {
    if (confirmed) void queryClient.invalidateQueries({ queryKey: batchKeys.all })
  }, [confirmed, queryClient])

  return query
}
