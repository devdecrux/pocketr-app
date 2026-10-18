import { describe, expect, it } from 'vitest'
import { HTTPError } from 'ky'
import { resolveErrorMessage } from '@/api/errors'
import { httpError } from '@/__tests__/support/fixtures'

function emptyBodyError(): HTTPError {
  const response = new Response('', { status: 500 })
  return new HTTPError(response, new Request('http://localhost/api'), {} as never)
}

describe('resolveErrorMessage', () => {
  it('returns the message of the backend error payload', async () => {
    expect(await resolveErrorMessage(httpError('Account still has a balance'), 'Failed')).toBe(
      'Account still has a balance',
    )
  })

  it('returns the fallback for non-HTTP errors and HTTP errors without a usable message', async () => {
    const cases = {
      'non-HTTP error': new Error('network down'),
      'HTTP error without a body': emptyBodyError(),
      'HTTP error with a blank message': httpError('   '),
    }
    const results = Object.fromEntries(
      await Promise.all(
        Object.entries(cases).map(async ([label, error]) => [
          label,
          await resolveErrorMessage(error, 'Failed'),
        ]),
      ),
    )

    expect(results).toEqual(
      Object.fromEntries(Object.keys(cases).map((label) => [label, 'Failed'])),
    )
  })
})
