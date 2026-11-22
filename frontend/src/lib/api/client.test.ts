import { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { ApiError, createApiClient, onUnauthorized, setBearerToken, takeBearerToken, toApiError } from './client'

afterEach(() => setBearerToken(null))

function axiosError(status?: number, data?: unknown, headers: Record<string, string> = {}): AxiosError {
  const config = { headers: new AxiosHeaders(headers) } as InternalAxiosRequestConfig
  const response = status === undefined ? undefined : { status, data, statusText: '', headers: {}, config }
  return new AxiosError('fail', 'ERR', config, undefined, response)
}

describe('toApiError', () => {
  it('Problem Details z backendu', () => {
    const error = toApiError(axiosError(403, { type: 'about:blank', title: 'Forbidden', status: 403, code: 'ROLE_MISSING', correlationId: 'c-1', detail: 'OPERATOR_ROLE is required' }))
    expect(error).toMatchObject({ status: 403, code: 'ROLE_MISSING', correlationId: 'c-1', message: 'OPERATOR_ROLE is required' })
  })
  it('brak odpowiedzi to błąd sieci, nie pusty wynik', () => {
    expect(toApiError(axiosError()).isNetworkError).toBe(true)
  })
  it('odpowiedź bez Problem Details', () => {
    expect(toApiError(axiosError(502, '<html>'))).toMatchObject({ status: 502, code: null })
  })
})

describe('token Bearer', () => {
  it('jest dokładany do żądań i znika po takeBearerToken', async () => {
    const seen: (string | undefined)[] = []
    const client = createApiClient('http://test')
    client.defaults.adapter = async (config) => {
      seen.push(config.headers.get('Authorization') as string | undefined)
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }
    setBearerToken('abc')
    await client.get('/x')
    expect(takeBearerToken()).toBe('abc')
    await client.get('/x')
    expect(seen).toEqual(['Bearer abc', undefined])
  })
  it('nie trafia do localStorage ani sessionStorage', () => {
    setBearerToken('secret-token')
    expect(JSON.stringify({ ...localStorage })).not.toContain('secret-token')
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('secret-token')
  })
  it('401 na żądaniu z tokenem powiadamia sesję, bez tokenu nie', async () => {
    let calls = 0
    const stop = onUnauthorized(() => calls++)
    const client = createApiClient('http://test')
    client.defaults.adapter = async (config) => {
      throw new AxiosError('401', 'ERR', config, undefined, { status: 401, data: {}, statusText: '', headers: {}, config })
    }
    await expect(client.get('/x')).rejects.toBeInstanceOf(ApiError)
    setBearerToken('abc')
    await expect(client.get('/x')).rejects.toMatchObject({ status: 401 })
    stop()
    expect(calls).toBe(1)
  })
})
