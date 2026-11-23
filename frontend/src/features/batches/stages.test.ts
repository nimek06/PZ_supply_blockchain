import { expect, it } from 'vitest'
import { normalizeBatchNumber } from './stages'

it('numer partii B-RRRR-NNNN', () => {
  expect(normalizeBatchNumber(' b-2026-0001 ')).toBe('B-2026-0001')
  expect(normalizeBatchNumber('B-2026-1')).toBeNull()
  expect(normalizeBatchNumber('https://evil.example/B-2026-0001')).toBeNull()
  expect(normalizeBatchNumber('')).toBeNull()
})
