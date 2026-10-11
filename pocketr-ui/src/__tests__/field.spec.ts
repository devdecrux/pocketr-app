import { describe, expect, it } from 'vitest'
import { FIELD_BASE_CLASS, MENU_CONTENT_CLASS, SELECT_CONTENT_CLASS } from '@/styles/field'
import { uiTheme } from '@/styles/theme'

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
    expect(MENU_CONTENT_CLASS).toContain('w-max')
    expect(MENU_CONTENT_CLASS).toContain('min-w-(--reka-combobox-trigger-width)')
    expect(MENU_CONTENT_CLASS).toContain('max-w-[min(24rem,calc(100vw-2rem))]')
  })
})

describe('uiTheme', () => {
  it('gives every select and searchable select the open list width rule', () => {
    expect(uiTheme.select.compoundVariants[0]?.class.content).toBe(SELECT_CONTENT_CLASS)
    expect(uiTheme.selectMenu.compoundVariants[0]?.class.content).toBe(MENU_CONTENT_CLASS)
  })

  it('gives text fields the field size, and action buttons (size xl) 44px below lg and 40px from lg', () => {
    expect(uiTheme.input.compoundVariants[0]?.class.base).toBe(FIELD_BASE_CLASS)
    const action = uiTheme.button.compoundVariants[0]?.class.base.split(' ')
    expect(action).toContain('h-11')
    expect(action).toContain('lg:h-10')
    expect(action).toContain('rounded-lg')
    expect(action).toContain('justify-center')
  })
})
