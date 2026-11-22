import { beforeEach, expect, it } from 'vitest'
import { clearIntent, loadIntent, newIntent, saveIntent } from './createBatchIntent'

beforeEach(() => sessionStorage.clear())

it('zamiar jest osobny dla każdego konta, bez względu na wielkość liter adresu', () => {
  const intent = newIntent(5)
  saveIntent('0xABC', intent)
  expect(loadIntent('0xabc')).toEqual(intent)
  expect(loadIntent('0xdef')).toBeNull()
  clearIntent('0xAbc')
  expect(loadIntent('0xabc')).toBeNull()
})

it('każdy nowy zamiar ma nowy klucz', () => {
  expect(newIntent().idempotencyKey).not.toBe(newIntent().idempotencyKey)
})

it('uszkodzony wpis w storage jest ignorowany', () => {
  sessionStorage.setItem('create-batch-intent:0xabc', '{nie json')
  expect(loadIntent('0xabc')).toBeNull()
  sessionStorage.setItem('create-batch-intent:0xabc', JSON.stringify({ foo: 1 }))
  expect(loadIntent('0xabc')).toBeNull()
})
