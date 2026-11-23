import { UserRejectedRequestError } from 'viem'
import { expect, it } from 'vitest'
import { isUserRejection } from './walletErrors'

it('rozpoznaje odmowę w portfelu także owiniętą w inny błąd', () => {
  expect(isUserRejection(new UserRejectedRequestError(new Error('nope')))).toBe(true)
  expect(isUserRejection({ code: 4001 })).toBe(true)
  expect(isUserRejection(new Error('outer', { cause: { code: 4001 } }))).toBe(true)
  expect(isUserRejection(new Error('timeout'))).toBe(false)
  expect(isUserRejection(null)).toBe(false)
})
