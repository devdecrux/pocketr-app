import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

interface CssRule {
  selector: string
  body: string
}

interface ThemeScope {
  name: string
  selector: string
  tokens: string[]
}

const themeCss = readFileSync(resolve(process.cwd(), 'src/main.css'), 'utf8')

function stripComments(value: string): string {
  return value.replace(/\/\*[\s\S]*?\*\//g, '').trim()
}

function extractTopLevelRules(source: string): CssRule[] {
  const rules: CssRule[] = []
  let depth = 0
  let selectorStart = 0
  let currentSelector = ''
  let bodyStart = 0

  for (let index = 0; index < source.length; index += 1) {
    const char = source[index]

    if (char === '{') {
      if (depth === 0) {
        currentSelector = stripComments(source.slice(selectorStart, index))
        bodyStart = index + 1
      }
      depth += 1
      continue
    }

    if (char !== '}') continue

    depth -= 1
    if (depth === 0) {
      rules.push({
        selector: currentSelector,
        body: source.slice(bodyStart, index),
      })
      selectorStart = index + 1
    }
  }

  return rules
}

function extractTokens(body: string): string[] {
  const tokens: string[] = []

  for (const match of body.matchAll(/(--[a-zA-Z0-9_-]+)\s*:/g)) {
    const token = match[1]
    if (token) {
      tokens.push(token)
    }
  }

  return tokens
}

function uniqueSorted(values: string[]): string[] {
  return [...new Set(values)].sort((left, right) => left.localeCompare(right))
}

function describeThemeScope(selector: string): string {
  const preset = selector.match(/data-theme-preset=['"]([^'"]+)['"]/)?.[1] ?? 'unknown'
  const mode = selector.includes('.dark') ? 'dark' : 'light'
  return `${preset} ${mode}`
}

function extractThemeScopes(source: string): ThemeScope[] {
  return extractTopLevelRules(source)
    .filter((rule) => rule.selector.includes('data-theme-preset'))
    .map((rule) => ({
      name: describeThemeScope(rule.selector),
      selector: rule.selector,
      tokens: extractTokens(rule.body),
    }))
}

function duplicateTokens(tokens: string[]): string[] {
  const seen = new Set<string>()
  const duplicates = new Set<string>()

  for (const token of tokens) {
    if (seen.has(token)) {
      duplicates.add(token)
    }
    seen.add(token)
  }

  return uniqueSorted([...duplicates])
}

describe('theme token contract', () => {
  const themeScopes = extractThemeScopes(themeCss)

  it('discovers the current light and dark theme scopes', () => {
    expect(themeScopes.map((scope) => scope.name)).toEqual(['pocketr light', 'pocketr dark'])
  })

  it('keeps every theme scope on the same token contract', () => {
    const [baselineScope] = themeScopes
    if (!baselineScope) {
      throw new Error('No theme scopes were found.')
    }

    const baselineTokens = uniqueSorted(baselineScope.tokens)

    for (const scope of themeScopes) {
      const scopeTokens = uniqueSorted(scope.tokens)
      const missing = baselineTokens.filter((token) => !scopeTokens.includes(token))
      const extra = scopeTokens.filter((token) => !baselineTokens.includes(token))

      if (missing.length || extra.length) {
        throw new Error(
          [
            `${scope.name} does not match ${baselineScope.name} theme tokens.`,
            missing.length ? `Missing: ${missing.join(', ')}` : '',
            extra.length ? `Extra: ${extra.join(', ')}` : '',
          ]
            .filter(Boolean)
            .join('\n'),
        )
      }
    }
  })

  it('does not duplicate token declarations inside a theme scope', () => {
    for (const scope of themeScopes) {
      expect(duplicateTokens(scope.tokens), `${scope.name} duplicate tokens`).toEqual([])
    }
  })
})
