import { describe, expect, it } from 'vitest'
import {
  FIELD_BASE_CLASS,
  FIELD_MENU_UI,
  FIELD_SELECT_UI,
  SELECT_CONTENT_CLASS,
} from '@/components/forms/fieldStyles'

describe('FIELD_BASE_CLASS', () => {
  it('is a 44px, 16px-text control below lg and a 40px, 14px-text control from lg', () => {
    const classes = FIELD_BASE_CLASS.split(' ')
    expect(classes).toContain('h-10')
    expect(classes).toContain('max-lg:h-11')
    expect(classes).toContain('text-sm')
    expect(classes).toContain('max-lg:text-base')
  })

  it('opens select lists at least as wide as the trigger and as the longest option, capped', () => {
    expect(SELECT_CONTENT_CLASS).toContain('w-max')
    expect(SELECT_CONTENT_CLASS).toContain('min-w-(--reka-select-trigger-width)')
    expect(SELECT_CONTENT_CLASS).toContain('max-w-[min(24rem,calc(100vw-2rem))]')
    expect(FIELD_SELECT_UI.content).toBe(SELECT_CONTENT_CLASS)
    expect(FIELD_MENU_UI.content).toContain('w-max')
    expect(FIELD_MENU_UI.content).toContain('min-w-(--reka-combobox-trigger-width)')
    expect(FIELD_MENU_UI.content).toContain('max-w-[min(24rem,calc(100vw-2rem))]')
  })
})
