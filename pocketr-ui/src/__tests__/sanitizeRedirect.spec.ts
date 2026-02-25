import { describe, expect, it } from 'vitest'
import { sanitizeInternalRedirect } from '../utils/sanitizeRedirect'

describe('sanitizeInternalRedirect', () => {
  it('accepts a valid internal route', () => {
    expect(sanitizeInternalRedirect('/dashboard')).toBe('/dashboard')
  })

  it('accepts nested internal routes', () => {
    expect(sanitizeInternalRedirect('/accounts/123')).toBe('/accounts/123')
  })

  it('accepts routes with query params', () => {
    expect(sanitizeInternalRedirect('/accounts?tab=ledger')).toBe('/accounts?tab=ledger')
  })

  it('rejects an external URL with https', () => {
    expect(sanitizeInternalRedirect('https://evil.com')).toBeNull()
  })

  it('rejects an external URL with http', () => {
    expect(sanitizeInternalRedirect('http://evil.com')).toBeNull()
  })

  it('rejects protocol-relative URLs', () => {
    expect(sanitizeInternalRedirect('//evil.com')).toBeNull()
  })

  it('rejects javascript: protocol', () => {
    expect(sanitizeInternalRedirect('javascript:alert(1)')).toBeNull()
  })

  it('returns null for null input', () => {
    expect(sanitizeInternalRedirect(null)).toBeNull()
  })

  it('returns null for undefined input', () => {
    expect(sanitizeInternalRedirect(undefined)).toBeNull()
  })

  it('returns null for empty string', () => {
    expect(sanitizeInternalRedirect('')).toBeNull()
  })

  it('rejects non-string input', () => {
    expect(sanitizeInternalRedirect(42)).toBeNull()
    expect(sanitizeInternalRedirect(['foo'])).toBeNull()
  })

  it('rejects bare domain strings', () => {
    expect(sanitizeInternalRedirect('evil.com')).toBeNull()
  })

  it('accepts routes with hash fragments', () => {
    expect(sanitizeInternalRedirect('/accounts#section')).toBe('/accounts#section')
  })

  it('rejects relative path without leading slash', () => {
    expect(sanitizeInternalRedirect('dashboard')).toBeNull()
  })
})
