import { api } from './client'
import type { CommandStatus } from './types'

export async function getCommand(commandId: string, signal?: AbortSignal): Promise<CommandStatus> {
  const { data } = await api.get<CommandStatus>(`/commands/${encodeURIComponent(commandId)}`, { signal })
  return data
}
