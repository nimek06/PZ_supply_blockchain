import axios, { AxiosError, type AxiosInstance } from 'axios'
import { config } from '../config'
import type { Problem } from './types'

// Wspólny klient HTTP. Token Bearer jest tylko w pamięci tego modułu: znika po odświeżeniu strony,
// nie ma go w localStorage ani w cookie.

let bearerToken: string | null = null

export function setBearerToken(token: string | null): void {
  bearerToken = token
}

type UnauthorizedListener = () => void
const unauthorizedListeners = new Set<UnauthorizedListener>()

/** Wywoływane, gdy backend odrzuci żądanie wysłane z tokenem (401). Zwraca funkcję wypisania. */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

/** Zabiera token z pamięci, żeby kolejne żądania już go nie wysłały. */
export function takeBearerToken(): string | null {
  const token = bearerToken
  bearerToken = null
  return token
}

/** Błąd z backendu (Problem Details) albo brak połączenia. Nigdy nie udaje pustej odpowiedzi. */
export class ApiError extends Error {
  readonly status: number | null
  readonly code: string | null
  readonly correlationId: string | null

  constructor(message: string, status: number | null, code: string | null, correlationId: string | null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.correlationId = correlationId
  }

  get isNetworkError(): boolean {
    return this.status === null
  }
}

function isProblem(data: unknown): data is Problem {
  return typeof data === 'object' && data !== null && 'code' in data && 'status' in data
}

export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  if (error instanceof AxiosError) {
    const response = error.response
    if (!response) {
      return new ApiError('Brak połączenia z backendem', null, null, null)
    }
    const data: unknown = response.data
    if (isProblem(data)) {
      return new ApiError(data.detail || data.title, response.status, data.code, data.correlationId)
    }
    return new ApiError(`Backend zwrócił ${response.status}`, response.status, null, null)
  }
  return new ApiError(error instanceof Error ? error.message : String(error), null, null, null)
}

export function createApiClient(baseURL: string): AxiosInstance {
  const client = axios.create({ baseURL, timeout: 10_000 })
  client.interceptors.request.use((request) => {
    if (bearerToken) request.headers.set('Authorization', `Bearer ${bearerToken}`)
    return request
  })
  client.interceptors.response.use(undefined, (error: unknown) => {
    const apiError = toApiError(error)
    const sentToken = error instanceof AxiosError && error.config?.headers?.has('Authorization')
    if (apiError.status === 401 && sentToken) unauthorizedListeners.forEach((listener) => listener())
    return Promise.reject(apiError)
  })
  return client
}

export const api = createApiClient(config.apiBaseUrl)
