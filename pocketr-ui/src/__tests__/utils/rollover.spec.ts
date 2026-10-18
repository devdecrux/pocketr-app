import { describe, expect, it } from 'vitest'
import { isValidRolloverDay } from '@/utils/rollover'

describe('isValidRolloverDay', () => {
  it('accepts whole days 1 to 31 only', () => {
    const valid = [1, 31]
    const invalid = [0, 32, 1.5, null]

    expect(valid.map((day) => [day, isValidRolloverDay(day)])).toEqual(
      valid.map((day) => [day, true]),
    )
    expect(invalid.map((day) => [day, isValidRolloverDay(day)])).toEqual(
      invalid.map((day) => [day, false]),
    )
  })
})
