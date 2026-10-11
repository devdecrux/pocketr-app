/**
 * Validates that a redirect target is a safe internal route.
 * Returns the path if valid, or null if it should be rejected.
 */
export function sanitizeInternalRedirect(redirect: unknown): string | null {
  if (typeof redirect !== 'string' || redirect.length === 0) {
    return null
  }

  // Must start with exactly one "/" — reject protocol-relative "//" and absolute URLs
  if (!redirect.startsWith('/') || redirect.startsWith('//')) {
    return null
  }

  // Reject backslashes: browsers treat a backslash like "/", so /\evil.example can act as //evil.example
  if (redirect.includes('\\')) {
    return null
  }

  // Reject anything that looks like it contains a protocol
  if (/^[a-z]+:/i.test(redirect)) {
    return null
  }

  return redirect
}
