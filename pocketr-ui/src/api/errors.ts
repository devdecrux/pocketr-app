import { HTTPError } from 'ky'

/**
 * The user-facing message of a failed request: the `message` of the backend's error payload when
 * there is one, otherwise `fallback`.
 */
export async function resolveErrorMessage(error: unknown, fallback: string): Promise<string> {
  if (!(error instanceof HTTPError)) return fallback
  const payload = await error.response.json<{ message?: string }>().catch(() => null)
  return payload?.message?.trim() || fallback
}
