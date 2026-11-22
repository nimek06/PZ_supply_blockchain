import { QueryClient } from '@tanstack/react-query'
import { ApiError } from '../lib/api/client'

// Ponawiamy tylko błędy przejściowe (sieć, 5xx). 4xx to odpowiedź, nie awaria.
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= 2) return false
  if (error instanceof ApiError) return error.status === null || error.status >= 500
  return false
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  })
}
