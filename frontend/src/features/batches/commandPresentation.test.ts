import type { Query } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { ApiError } from '../../lib/api/client'
import type { CommandState, CommandStatus } from '../../lib/api/types'
import { COMMAND_POLL_MS, commandRefetchInterval } from './batchQueries'
import { describeCommand, isTerminal } from './commandPresentation'

const ALL: CommandState[] = ['ACCEPTED', 'PINNING', 'WAITING_FOR_QA', 'READY_TO_SUBMIT', 'SUBMITTED', 'CONFIRMED', 'FAILED']

describe('describeCommand', () => {
  it('tylko CONFIRMED z wynikiem ACCEPTED jest sukcesem', () => {
    for (const status of ALL) {
      const view = describeCommand({ status, outcome: status === 'CONFIRMED' ? 'ACCEPTED' : null, type: 'STAGE' })
      expect(view.tone === 'success').toBe(status === 'CONFIRMED')
    }
  })
  it('przyjęcie (202) i wysłana transakcja to wciąż oczekiwanie', () => {
    expect(describeCommand({ status: 'ACCEPTED', outcome: null, type: 'STAGE' }).tone).toBe('pending')
    expect(describeCommand({ status: 'SUBMITTED', outcome: null, type: 'STAGE' }).tone).toBe('pending')
  })
  it('StageRejected to odrzucenie, nie sukces', () => {
    const view = describeCommand({ status: 'CONFIRMED', outcome: 'REJECTED', type: 'STAGE' })
    expect(view.label).toBe('Odrzucone')
    expect(view.tone).toBe('danger')
    expect(view.terminal).toBe(true)
  })
  it('kontrola czeka na drugi podpis', () => {
    expect(describeCommand({ status: 'WAITING_FOR_QA', outcome: null, type: 'STAGE' }).label).toMatch(/QA/)
  })
  it('końcowe są tylko CONFIRMED i FAILED', () => {
    expect(ALL.filter(isTerminal)).toEqual(['CONFIRMED', 'FAILED'])
  })
})

function queryWith(data?: Partial<CommandStatus>, error?: Error) {
  return { state: { data, error } } as unknown as Query<CommandStatus, Error>
}

describe('commandRefetchInterval', () => {
  it('odpytuje, dopóki polecenie trwa', () => {
    expect(commandRefetchInterval(queryWith({ status: 'SUBMITTED' }))).toBe(COMMAND_POLL_MS)
  })
  it('kończy na wyniku końcowym', () => {
    expect(commandRefetchInterval(queryWith({ status: 'CONFIRMED' }))).toBe(false)
    expect(commandRefetchInterval(queryWith({ status: 'FAILED' }))).toBe(false)
  })
  it('kończy na trwałym błędzie, ponawia przy braku sieci i 5xx', () => {
    expect(commandRefetchInterval(queryWith(undefined, new ApiError('x', 404, 'NOT_FOUND', null)))).toBe(false)
    expect(commandRefetchInterval(queryWith(undefined, new ApiError('x', 401, 'UNAUTHENTICATED', null)))).toBe(false)
    expect(commandRefetchInterval(queryWith(undefined, new ApiError('x', null, null, null)))).toBe(COMMAND_POLL_MS)
    expect(commandRefetchInterval(queryWith(undefined, new ApiError('x', 503, 'UNAVAILABLE', null)))).toBe(COMMAND_POLL_MS)
  })
})
