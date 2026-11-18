import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/feedback'
import { api, ApiError } from '../../lib/api'
import { FollowUpsPage } from './FollowUpsPage'
import type { FollowUp } from './types'

vi.mock('../../lib/api', async importOriginal => ({
  ...await importOriginal<typeof import('../../lib/api')>(),
  api: vi.fn(),
}))
const apiMock = vi.mocked(api)

beforeEach(() => { apiMock.mockReset() })

it('loads overdue tasks from the URL, retries a load failure, and completes the task', async () => {
  const item: FollowUp = {
    id: 9,
    leadId: 13,
    leadName: 'Ishita Demo',
    canOpenLead: true,
    assigneeId: 4,
    assigneeName: 'Demo Agent',
    note: 'Check preferred date for the site visit',
    dueAt: '2026-11-15T08:00:00Z',
    createdAt: '2026-11-14T08:00:00Z',
    updatedAt: '2026-11-14T08:00:00Z',
    completedAt: null,
    status: 'OVERDUE',
  }
  const path = '/api/follow-ups?view=OVERDUE&page=0&size=10'
  apiMock.mockRejectedValueOnce(new ApiError(503, 'Follow-ups are temporarily unavailable.'))
  apiMock.mockResolvedValueOnce({ items: [item], totalElements: 1, totalPages: 1, page: 0, size: 10 })
  apiMock.mockResolvedValueOnce({ ...item, status: 'COMPLETED', completedAt: '2026-11-18T08:00:00Z' })
  apiMock.mockResolvedValueOnce({ items: [], totalElements: 0, totalPages: 0, page: 0, size: 10 })

  render(<MemoryRouter initialEntries={['/follow-ups?view=OVERDUE']}><ToastProvider><FollowUpsPage /></ToastProvider></MemoryRouter>)
  expect(await screen.findByRole('alert')).toHaveTextContent('Follow-ups are temporarily unavailable.')
  expect(screen.getByRole('button', { name: 'Overdue' })).toHaveAttribute('aria-pressed', 'true')
  await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
  expect(await screen.findByText(item.note)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Ishita Demo' })).toHaveAttribute('href', '/leads/13')
  await userEvent.click(screen.getByRole('button', { name: 'Mark complete' }))

  expect(await screen.findByText('No overdue follow-ups')).toBeInTheDocument()
  expect(screen.getByText('Follow-up completed.')).toBeInTheDocument()
  expect(apiMock).toHaveBeenNthCalledWith(1, path, expect.objectContaining({ signal: expect.anything() }))
  expect(apiMock).toHaveBeenNthCalledWith(3, '/api/follow-ups/9', { method: 'PATCH', body: '{"completed":true}' })
})
