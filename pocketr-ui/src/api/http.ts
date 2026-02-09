import ky from 'ky'

function getCookieValue(name: string): string | null {
  if (typeof document === 'undefined') {
    return null
  }

  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith(`${name}=`))

  if (!cookie) {
    return null
  }

  return cookie.split('=').slice(1).join('=')
}

export const api = ky.create({
  credentials: 'include',
  hooks: {
    beforeRequest: [
      (request) => {
        const xsrfToken = getCookieValue('XSRF-TOKEN')

        if (xsrfToken) {
          request.headers.set('X-XSRF-TOKEN', decodeURIComponent(xsrfToken))
        }
      },
    ],
  },
  retry: 0,
})
