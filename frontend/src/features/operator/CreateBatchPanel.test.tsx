import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderWithProviders } from '../../../tests/render'
import type { SessionState } from '../../app/sessionState'
import { ApiError } from '../../lib/api/client'
import type { CommandStatus } from '../../lib/api/types'
import { CreateBatchPanel } from './CreateBatchPanel'
import { loadIntent, saveIntent } from './createBatchIntent'

const createBatch = vi.fn()
const getCommand = vi.fn()
const getBatchByNumber = vi.fn()

vi.mock('../../lib/api/batchesApi', () => ({
  createBatch: (key: string) => createBatch(key),
  getBatchByNumber: (n: string) => getBatchByNumber(n),
}))
vi.mock('../../lib/api/commandsApi', () => ({ getCommand: (id: string) => getCommand(id) }))

const OPERATOR = '0x90f79bf6eb2c4f870365e785982e1f101e93b906'
const COMMAND_ID = '11111111-1111-4111-8111-111111111111'

function session(roles: ('OPERATOR_ROLE' | 'QA_INSPECTOR_ROLE')[]): SessionState {
  return {
    status: 'signed-in',
    context: { address: OPERATOR, roles, expiresAt: 1_900_000_000, chainId: 31337, verifyingContract: '0x5FbDB2315678afecb367f032d93F642f64180aa3' },
  }
}

const accepted = { commandId: COMMAND_ID, type: 'CREATE_BATCH', status: 'ACCEPTED', statusUrl: `/api/v1/commands/${COMMAND_ID}`, batchNumber: 'B-2026-0042' }

function command(fields: Partial<CommandStatus>): CommandStatus {
  return { commandId: COMMAND_ID, type: 'CREATE_BATCH', status: 'ACCEPTED', outcome: null, batchNumber: 'B-2026-0042', ...fields } as CommandStatus
}

beforeEach(() => {
  sessionStorage.clear()
  createBatch.mockReset()
  getCommand.mockReset()
  getBatchByNumber.mockReset()
})

describe('kto widzi formularz', () => {
  it('niezalogowany', () => {
    renderWithProviders(<CreateBatchPanel source="api" />)
    expect(screen.getByText(/Zaloguj się portfelem/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Utwórz partię' })).not.toBeInTheDocument()
  })
  it('zalogowany bez roli operatora', () => {
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['QA_INSPECTOR_ROLE']) })
    expect(screen.getByText(/nie ma roli operatora/)).toBeInTheDocument()
  })
  it('dane demo nie tworzą partii', () => {
    renderWithProviders(<CreateBatchPanel source="demo" />, { session: session(['OPERATOR_ROLE']) })
    expect(screen.getByText(/tylko na prawdziwym API/)).toBeInTheDocument()
  })
})

describe('tworzenie partii', () => {
  it('202 to dopiero przyjęcie, potwierdzenie przychodzi z pollingu', async () => {
    createBatch.mockResolvedValue(accepted)
    getCommand.mockResolvedValueOnce(command({ status: 'ACCEPTED' }))
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['OPERATOR_ROLE']) })

    await userEvent.click(screen.getByRole('button', { name: 'Utwórz partię' }))
    expect(await screen.findByText('Przyjęte')).toBeInTheDocument()
    expect(screen.queryByText('Potwierdzone')).not.toBeInTheDocument()
    expect(loadIntent(OPERATOR)).toMatchObject({ commandId: COMMAND_ID, batchNumber: 'B-2026-0042' })
  })

  it('po błędzie sieci ponowienie wysyła ten sam Idempotency-Key', async () => {
    createBatch.mockRejectedValueOnce(new ApiError('Brak połączenia z backendem', null, null, null)).mockResolvedValueOnce(accepted)
    getCommand.mockResolvedValue(command({ status: 'SUBMITTED', txHash: `0x${'a'.repeat(64)}` }))
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['OPERATOR_ROLE']) })

    await userEvent.click(screen.getByRole('button', { name: 'Utwórz partię' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Ponów to samo zgłoszenie' }))
    expect(await screen.findByText('Wysłane')).toBeInTheDocument()
    expect(createBatch).toHaveBeenCalledTimes(2)
    expect(createBatch.mock.calls[0]![0]).toBe(createBatch.mock.calls[1]![0])
  })

  it('po odświeżeniu strony status wraca z zapisanego zamiaru', async () => {
    saveIntent(OPERATOR, { idempotencyKey: 'k-1', createdAt: 1, commandId: COMMAND_ID, batchNumber: 'B-2026-0042' })
    getCommand.mockResolvedValue(command({ status: 'CONFIRMED', outcome: null }))
    getBatchByNumber.mockResolvedValue({
      batchId: `0x${'b'.repeat(64)}`, batchNumber: 'B-2026-0042', completed: false, nextStage: 0,
      projection: { lastIndexedBlock: 7, requiredConfirmations: 1, stale: false },
    })
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['OPERATOR_ROLE']) })

    expect(await screen.findByText('Potwierdzone')).toBeInTheDocument()
    expect(await screen.findByText('Partia B-2026-0042')).toBeInTheDocument()
    expect(screen.getByText(/Czeka na raport stacji: przygotowanie \(etap 0\)/)).toBeInTheDocument()
    expect(createBatch).not.toHaveBeenCalled()
  })

  it('zamiar bez odpowiedzi po odświeżeniu: ponowienie tym samym kluczem', async () => {
    saveIntent(OPERATOR, { idempotencyKey: 'k-zapisany', createdAt: 1 })
    createBatch.mockResolvedValue(accepted)
    getCommand.mockResolvedValue(command({ status: 'ACCEPTED' }))
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['OPERATOR_ROLE']) })

    await userEvent.click(screen.getByRole('button', { name: 'Ponów to samo zgłoszenie' }))
    await waitFor(() => expect(createBatch).toHaveBeenCalledWith('k-zapisany'))
  })

  it('konflikt klucza (409) nie pozwala ponowić, tylko zacząć od nowa', async () => {
    createBatch.mockRejectedValue(new ApiError('Idempotency-Key użyty z inną treścią', 409, 'IDEMPOTENCY_CONFLICT', 'c-9'))
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['OPERATOR_ROLE']) })

    await userEvent.click(screen.getByRole('button', { name: 'Utwórz partię' }))
    expect(await screen.findByText(/Idempotency-Key użyty/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ponów to samo zgłoszenie' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Zacznij od nowa' }))
    expect(screen.getByRole('button', { name: 'Utwórz partię' })).toBeInTheDocument()
    expect(loadIntent(OPERATOR)).toBeNull()
  })

  it('nieudane polecenie pokazuje błąd i pozwala zacząć od nowa', async () => {
    saveIntent(OPERATOR, { idempotencyKey: 'k-2', createdAt: 1, commandId: COMMAND_ID })
    getCommand.mockResolvedValue(command({ status: 'FAILED', failureCode: 'RPC_UNAVAILABLE' }))
    renderWithProviders(<CreateBatchPanel source="api" />, { session: session(['OPERATOR_ROLE']) })

    expect(await screen.findByText('Nieudane')).toBeInTheDocument()
    expect(screen.getByText('RPC_UNAVAILABLE')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Utwórz kolejną partię' }))
    expect(screen.getByRole('button', { name: 'Utwórz partię' })).toBeInTheDocument()
  })
})
