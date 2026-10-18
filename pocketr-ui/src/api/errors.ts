import { HTTPError } from 'ky'

/**
 * The user-facing message of a failed request: the `message` of the backend's error payload when
 * there is one, otherwise `fallback`.
 */
export async function resolveErrorMessage(error: unknown, fallback: string): Promise<string> {
  if (!(error instanceof HTTPError)) return fallback
  // Ky consumes the response body itself and exposes the parsed JSON payload as `error.data`.
  const payload = error.data as { message?: unknown } | string | undefined
  if (typeof payload !== 'object' || payload === null) return fallback
  return (typeof payload.message === 'string' && payload.message.trim()) || fallback
}
