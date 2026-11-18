import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import LoginPage from './LoginPage'

const account = { id: 5, name: 'Demo Agent', email: 'agent@example.test', role: 'SALES_AGENT', active: true }
const token = { headerName: 'X-CSRF-TOKEN', token: 'local-test-token' }

function showLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<h1>Workspace ready</h1>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('focuses the first invalid field and does not submit incomplete credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({}, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    showLogin()
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: 'Email address' })).toHaveFocus()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('submits credentials with the server-provided CSRF token and opens the workspace', async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(Response.json({}, { status: 401 }))
      .mockResolvedValueOnce(Response.json(token))
      .mockResolvedValueOnce(Response.json(account))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    showLogin()
    await user.type(screen.getByLabelText('Email address'), account.email)
    await user.type(screen.getByLabelText('Password'), 'demo-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Workspace ready' })).toBeInTheDocument()
    const [url, init] = fetchMock.mock.calls[2]
    expect(url).toBe('/api/auth/login')
    expect(init?.credentials).toBe('include')
    expect(new Headers(init?.headers).get(token.headerName)).toBe(token.token)
    expect(JSON.parse(String(init?.body))).toEqual({ email: account.email, password: 'demo-password' })
  })

  it('displays the API error and keeps the login form available after rejected credentials', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(Response.json({}, { status: 401 }))
      .mockResolvedValueOnce(Response.json(token))
      .mockResolvedValueOnce(Response.json({ detail: 'Email or password is incorrect.' }, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    showLogin()
    await user.type(screen.getByLabelText('Email address'), account.email)
    await user.type(screen.getByLabelText('Password'), 'incorrect')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled()
  })
})
