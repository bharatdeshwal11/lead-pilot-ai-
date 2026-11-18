export const SESSION_EXPIRED = 'leadpilot:session-expired'

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

type ErrorBody = { detail?: string; message?: string; fieldErrors?: Record<string, string> }
type CsrfToken = { token: string; headerName: string }

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')

async function request(path: string, init: RequestInit): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, { ...init, credentials: 'include' })
  } catch (error) {
    if (init.signal?.aborted) throw error
    throw new ApiError(0, 'Could not reach LeadPilot. Check your connection and try again.')
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null) as ErrorBody | null
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event(SESSION_EXPIRED))
    }
    const message = body?.detail || body?.message || defaultError(response.status)
    throw new ApiError(response.status, message, body?.fieldErrors)
  }
  return response
}

function defaultError(status: number): string {
  if (status === 401) return 'Sign in to continue.'
  if (status === 403) return 'You do not have permission to do that.'
  if (status === 404) return 'The requested item could not be found.'
  if (status === 429) return 'Too many requests. Try again in a moment.'
  return 'The request could not be completed. Try again.'
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method || 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
    const csrfResponse = await request('/api/auth/csrf', { signal: init.signal, cache: 'no-store' })
    const csrf = await csrfResponse.json() as CsrfToken
    headers.set(csrf.headerName, csrf.token)
  }
  const response = await request(path, { ...init, method, headers })
  if (response.status === 204 || response.status === 205 || method === 'HEAD') return undefined as T
  return response.json() as Promise<T>
}

export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Something went wrong. Try again.'
}
