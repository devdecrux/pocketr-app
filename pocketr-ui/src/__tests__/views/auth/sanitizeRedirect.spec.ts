import { describe, expect, it } from 'vitest'
import { sanitizeInternalRedirect } from '@/views/auth/sanitizeRedirect'

describe('sanitizeInternalRedirect', () => {
  it('accepts internal routes unchanged', () => {
    const safe = [
      '/dashboard',
      '/accounts/123',
      '/accounts?tab=ledger',
      '/accounts#section',
      '/transactions?page=1',
    ]

    expect(safe.map((redirect) => [redirect, sanitizeInternalRedirect(redirect)])).toEqual(
      safe.map((redirect) => [redirect, redirect]),
    )
  })

  it('rejects external, scheme, backslash, bare and non-string targets', () => {
    const unsafe: unknown[] = [
      'https://evil.com',
      'http://evil.com',
      '//evil.com',
      'javascript:alert(1)',
      '/\\evil.example',
      '\\\\evil.example',
      '/foo\\bar',
      'evil.com',
      'dashboard',
      null,
      undefined,
      '',
      42,
      ['foo'],
    ]

    expect(unsafe.map((redirect) => [redirect, sanitizeInternalRedirect(redirect)])).toEqual(
      unsafe.map((redirect) => [redirect, null]),
    )
  })
})
