import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../../components/feedback'
import { api, ApiError } from '../../lib/api'
import { LeadDetailPage } from './LeadDetailPage'
import { CreateLeadPage } from './LeadForm'
import { ReplyPanel } from './ReplyPanel'
import type { Lead, ReplyDraft } from './types'

vi.mock('../../auth/AuthProvider', () => ({ useAuth: () => ({ user: { id: 1, name: 'Demo Admin', role: 'ADMIN' } }) }))
vi.mock('../../lib/api', async importOriginal => ({
  ...await importOriginal<typeof import('../../lib/api')>(),
  api: vi.fn(),
}))

const apiMock = vi.mocked(api)
const testLead: Lead = {
  id: 13,
  customerName: 'Ishita Demo',
  email: 'ishita@example.test',
  phone: null,
  enquiryMessage: 'I would like a 3BHK in Greater Noida with a site visit next week.',
  source: 'MANUAL',
  priority: 'MEDIUM',
  status: 'NEW',
  assignedUser: null,
  createdAt: '2026-11-18T10:00:00Z',
  updatedAt: '2026-11-18T10:00:00Z',
}
const testDraft: ReplyDraft = {
  id: 21,
  leadId: testLead.id,
  body: 'Thank you for sharing your enquiry. When are you available for a call?',
  status: 'DRAFT',
  provider: 'mock',
  createdAt: testLead.createdAt,
  updatedAt: testLead.updatedAt,
}

function renderCreate() {
  render(<MemoryRouter initialEntries={['/leads/new']}><ToastProvider><Routes>
    <Route path="/leads/new" element={<CreateLeadPage />} />
    <Route path="/leads/:id" element={<h1>Lead record opened</h1>} />
  </Routes></ToastProvider></MemoryRouter>)
}

function fillLead() {
  fireEvent.change(screen.getByRole('textbox', { name: 'Customer name' }), { target: { value: testLead.customerName } })
  fireEvent.change(screen.getByRole('textbox', { name: 'Email address' }), { target: { value: testLead.email } })
  fireEvent.change(screen.getByRole('combobox', { name: 'Source' }), { target: { value: 'MANUAL' } })
  fireEvent.change(screen.getByRole('textbox', { name: 'Enquiry message' }), { target: { value: testLead.enquiryMessage } })
}

describe('lead workflow', () => {
  beforeEach(() => { apiMock.mockReset() })

  it('keeps incomplete enquiries local and marks invalid fields', async () => {
    apiMock.mockResolvedValue([])
    renderCreate()
    const email = screen.getByRole('textbox', { name: 'Email address' })
    const source = screen.getByRole('combobox', { name: 'Source' })
    fireEvent.change(email, { target: { value: 'invalid email' } })
    await userEvent.click(screen.getByRole('button', { name: 'Create Lead' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Check the highlighted fields.')
    expect(email).toHaveAttribute('aria-invalid', 'true')
    expect(email).toHaveAccessibleDescription('Enter a valid email address.')
    expect(source).toHaveAccessibleDescription('Choose a lead source.')
    expect(apiMock.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
  })

  it('creates a lead with the submitted values and opens the returned lead record', async () => {
    apiMock.mockImplementation(async (path, options) => {
      if (path === '/api/leads' && options?.method === 'POST') return testLead
      return []
    })
    renderCreate()
    fillLead()
    await userEvent.click(screen.getByRole('button', { name: 'Create Lead' }))

    expect(await screen.findByRole('heading', { name: 'Lead record opened' })).toBeInTheDocument()
    expect(apiMock).toHaveBeenCalledWith('/api/leads', {
      method: 'POST',
      body: JSON.stringify({
        customerName: testLead.customerName,
        email: testLead.email,
        phone: null,
        source: 'MANUAL',
        enquiryMessage: testLead.enquiryMessage,
        status: 'NEW',
        priority: 'MEDIUM',
      }),
    })
    expect(screen.getByText('Lead created.')).toBeInTheDocument()
  })

  it('shows a server rejection and retains the enquiry for correction', async () => {
    apiMock.mockImplementation(async (_path, options) => {
      if (options?.method === 'POST') {
        throw new ApiError(409, 'This enquiry was already submitted.', { email: 'Check the lead for this email.' })
      }
      return []
    })
    renderCreate()
    fillLead()
    const email = screen.getByRole('textbox', { name: 'Email address' })
    await userEvent.click(screen.getByRole('button', { name: 'Create Lead' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This enquiry was already submitted.')
    expect(email).toHaveAccessibleDescription('Check the lead for this email.')
    expect(email).toHaveValue(testLead.email)
    expect(screen.getByRole('textbox', { name: 'Enquiry message' })).toHaveValue(testLead.enquiryMessage)
  })

  it('shows an analysis failure and keeps the lead enquiry available', async () => {
    apiMock.mockImplementation(async path => {
      if (path.endsWith('/analyze')) throw new ApiError(503, 'Analysis is temporarily unavailable.')
      if (path.endsWith('/analyses')) return []
      return testLead
    })
    render(<MemoryRouter initialEntries={['/leads/13']}><ToastProvider><Routes>
      <Route path="/leads/:id" element={<LeadDetailPage />} />
    </Routes></ToastProvider></MemoryRouter>)

    expect(await screen.findByText(testLead.enquiryMessage)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Analyze Lead' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Analysis is temporarily unavailable.')
    await userEvent.click(within(screen.getByRole('navigation', { name: 'Lead sections' })).getByRole('link', { name: 'Overview' }))
    expect(screen.getByText(testLead.enquiryMessage)).toBeInTheDocument()
  })

  it('submits an edited reply for review and makes the approved body read-only', async () => {
    apiMock.mockImplementation(async (path, options) => {
      if (path.endsWith('/reply-drafts')) return [testDraft]
      if (options?.method === 'PATCH') {
        return { ...testDraft, body: 'Can we arrange a call on Tuesday?', status: 'PENDING_REVIEW' }
      }
      if (path.endsWith('/approve')) {
        return { ...testDraft, body: 'Can we arrange a call on Tuesday?', status: 'APPROVED' }
      }
      throw new Error(`Unexpected API call ${path}`)
    })
    render(<ToastProvider><ReplyPanel leadId={testLead.id} /></ToastProvider>)
    const body = await screen.findByRole('textbox', { name: 'Reply body' })
    fireEvent.change(body, { target: { value: 'Can we arrange a call on Tuesday?' } })
    await userEvent.click(screen.getByRole('button', { name: 'Submit for Review' }))
    expect(await screen.findByText('Pending review')).toBeInTheDocument()
    expect(apiMock).toHaveBeenCalledWith('/api/reply-drafts/21', {
      method: 'PATCH',
      body: JSON.stringify({ body: 'Can we arrange a call on Tuesday?', status: 'PENDING_REVIEW' }),
    })
    await userEvent.click(screen.getByRole('button', { name: 'Approve Draft' }))

    expect(await screen.findByText('Approved', { exact: true })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Reply body' })).toHaveAttribute('readonly')
    expect(screen.getByRole('textbox', { name: 'Reply body' })).toHaveValue('Can we arrange a call on Tuesday?')
  })
})
