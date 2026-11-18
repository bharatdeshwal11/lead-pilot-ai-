import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, SESSION_EXPIRED } from './api'

describe('api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('keeps problem-details field errors from a rejected mutation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(Response.json({
        status: 400,
        detail: 'Please correct the input.',
        fieldErrors: { name: 'Enter the lead name.' },
      }, { status: 400 }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(api('/api/leads', { method: 'POST', body: '{}' })).rejects.toMatchObject({
      status: 400, message: 'Please correct the input.', fieldErrors: { name: 'Enter the lead name.' },
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('notifies the auth provider of an expired session on a protected route', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({}, { status: 401 })))
    const expired = vi.fn()
    window.addEventListener(SESSION_EXPIRED, expired, { once: true })
    try {
      await expect(api('/api/dashboard/stats')).rejects.toMatchObject({ status: 401 })
      expect(expired).toHaveBeenCalledOnce()
    } finally {
      window.removeEventListener(SESSION_EXPIRED, expired)
    }
  })
})
