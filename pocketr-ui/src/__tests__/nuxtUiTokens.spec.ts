import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(process.cwd(), 'src/main.css'), 'utf8').replace(
  /\/\*[\s\S]*?\*\//g,
  '',
)

/** Returns the declarations of the first block whose selector is exactly `selector` (any nesting level). */
function declarations(selector: string): Record<string, string> {
  const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const match = css.match(new RegExp(`(?:^|[}{;])\\s*${escaped}\\s*\\{([^{}]*)\\}`))
  if (!match?.[1]) {
    throw new Error(`No CSS block found for selector "${selector}".`)
  }

  const result: Record<string, string> = {}
  for (const [, property, value] of match[1].matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)) {
    if (property && value) {
      result[property] = value.trim()
    }
  }
  return result
}

describe('Pocketr v2 Nuxt UI tokens (IMPLEMENTATION.md "Visual rules")', () => {
  it('maps the light palette exactly', () => {
    expect(declarations(':root')).toEqual({
      '--ui-primary': '#0891b2',
      '--ui-secondary': '#f59e0b',
      '--ui-warning': '#f59e0b',
      '--ui-error': '#dc2626',
      '--ui-bg': '#ffffff',
      '--ui-border': '#e5e7eb',
      '--ui-border-muted': '#e5e7eb',
      '--ui-text': '#172124',
      '--ui-text-highlighted': '#172124',
      '--ui-text-muted': '#67747c',
      '--ui-text-dimmed': '#67747c',
      '--ui-text-toned': '#67747c',
      '--ui-text-inverted': '#ffffff',
      '--ui-bg-muted': '#fafafa',
      '--ui-bg-elevated': '#f0f2f5',
      '--ui-bg-accented': '#e5e7eb',
      '--ui-border-accented': '#e5e7eb',
      '--pocketr-bg-canvas': '#fafafa',
      '--pocketr-bg-main': '#fafafa',
      '--pocketr-bg-shell': '#ffffff',
      '--pocketr-header-height': '73px',
      '--pocketr-nav-active': '#e5f5f9',
      '--pocketr-icon-bg': '#eef9fc',
      '--pocketr-tile-bg': '#e8f7fb',
      '--pocketr-detail-bg': '#f1f6f9',
      '--pocketr-choice-active': '#dff3f8',
      '--pocketr-avatar-bg': '#dee3e8',
      '--pocketr-track': '#f0f2f5',
      '--pocketr-chart-grid': '#e5e7eb',
      '--pocketr-auth-header-bg': '#ffffff',
      '--pocketr-field-bg': '#ffffff',
      '--pocketr-auth-mark-bg': '#e7f7fd',
      '--pocketr-warning-fg': '#92400e',
      '--pocketr-success-fg': '#047857',
      '--pocketr-scrim': 'rgb(23 33 36 / 0.6)',
    })
  })

  it('maps the dark palette exactly', () => {
    expect(declarations('.dark')).toEqual({
      '--ui-primary': '#22d3ee',
      '--ui-secondary': '#f59e0b',
      '--ui-warning': '#f59e0b',
      '--ui-bg': '#1c2227',
      '--ui-bg-elevated': '#242c32',
      '--ui-border': '#30383f',
      '--ui-border-muted': '#30383f',
      '--ui-text': '#edf2f5',
      '--ui-text-highlighted': '#edf2f5',
      '--ui-text-muted': '#a0acb7',
      '--ui-text-dimmed': '#a0acb7',
      '--ui-text-toned': '#a0acb7',
      '--ui-text-inverted': '#082f3a',
      '--ui-bg-muted': '#15191d',
      '--ui-bg-accented': '#30383f',
      '--ui-border-accented': '#30383f',
      '--pocketr-bg-canvas': '#101214',
      '--pocketr-bg-main': '#15191d',
      '--pocketr-bg-shell': '#111518',
      '--pocketr-nav-active': '#11333e',
      '--pocketr-icon-bg': '#17313b',
      '--pocketr-tile-bg': '#17313b',
      '--pocketr-detail-bg': '#1f2a31',
      '--pocketr-choice-active': '#093644',
      '--pocketr-avatar-bg': '#2e3941',
      '--pocketr-track': '#28323a',
      '--pocketr-chart-grid': '#30383f',
      '--pocketr-auth-header-bg': '#161b20',
      '--pocketr-field-bg': '#1f262c',
      '--pocketr-auth-mark-bg': '#1c2b32',
      '--pocketr-warning-fg': '#fcd34d',
      '--pocketr-success-fg': '#6ee7b7',
      '--pocketr-scrim': 'rgb(0 0 0 / 0.2)',
    })
  })

  it('re-points shared legacy theme keys to Nuxt UI tokens only on migrated pages', () => {
    expect(declarations("body[data-ui-migration='v2']")).toEqual({
      '--primary': 'var(--ui-primary)',
      '--secondary': 'var(--ui-secondary)',
      '--muted': 'var(--ui-bg-muted)',
      '--border': 'var(--ui-border)',
      '--radius-xs': 'calc(var(--ui-radius) * 0.5)',
      '--radius-sm': 'var(--ui-radius)',
      '--radius-md': 'calc(var(--ui-radius) * 1.5)',
      '--radius-lg': 'calc(var(--ui-radius) * 2)',
      '--radius-xl': 'calc(var(--ui-radius) * 3)',
      '--radius-2xl': 'calc(var(--ui-radius) * 4)',
      '--radius-3xl': 'calc(var(--ui-radius) * 6)',
    })
  })

  it('keeps text-entry controls at 16px below lg on migrated pages only (no iOS focus zoom)', () => {
    const rule = css.match(
      /@media \(max-width: 1023\.98px\)\s*\{\s*body\[data-ui-migration='v2'\]\s*:is\(([^{}]*)\)\s*\{\s*font-size:\s*16px;\s*\}\s*\}/,
    )
    expect(rule?.[1]).toContain('input:not(')
    expect(rule?.[1]).toContain('textarea')
    expect(rule?.[1]).toContain('select')
  })
})
