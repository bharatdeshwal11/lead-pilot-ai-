import { ArrowRight, Eye, EyeOff, Radar, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Brand } from '../components/Brand'
import { InlineError } from '../components/feedback'
import { ApiError, errorMessage } from '../lib/api'
import { useAuth } from './AuthProvider'

export default function LoginPage() {
  const { checkSession, signIn, startupError, user } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [fields, setFields] = useState<Record<string, string>>({})
  const origin: unknown = location.state?.from
  const next = typeof origin === 'string' && origin.startsWith('/') && !origin.startsWith('//') ? origin : '/'

  if (user) return <Navigate to={next} replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const input = email.trim()
    const invalid: Record<string, string> = {}
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(input)) invalid.email = 'Enter a valid email address.'
    if (!password) invalid.password = 'Enter your password.'
    setFields(invalid)
    setError('')
    if (Object.keys(invalid).length) {
      event.currentTarget.querySelector<HTMLInputElement>(invalid.email ? '#login-email' : '#login-password')?.focus()
      return
    }
    setBusy(true)
    try {
      await signIn(input, password)
    } catch (failure) {
      setError(errorMessage(failure))
      if (failure instanceof ApiError) setFields(failure.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login">
      <section className="login-main">
        <Brand link={false} />
        <div className="login-form-wrap">
          <h1>Welcome back</h1>
          <p>Sign in to your sales workspace.</p>
          {startupError && !error && <InlineError message={startupError} retry={() => void checkSession()} />}
          {error && <InlineError message={error} />}
          <form className="login-form" onSubmit={submit} noValidate>
            <div className="field">
              <label htmlFor="login-email">Email address</label>
              <input id="login-email" type="email" autoComplete="username" placeholder="you@company.com" value={email}
                onChange={event => setEmail(event.target.value)} required aria-invalid={Boolean(fields.email)}
                aria-describedby={fields.email ? 'login-email-error' : undefined} />
              {fields.email && <small id="login-email-error" className="field-error">{fields.email}</small>}
            </div>
            <div className="field">
              <label htmlFor="login-password">Password</label>
              <div className="password-field">
                <input id="login-password" type={showPassword ? 'text' : 'password'} autoComplete="current-password"
                  placeholder="Enter your password" value={password} onChange={event => setPassword(event.target.value)}
                  required aria-invalid={Boolean(fields.password)} aria-describedby={fields.password ? 'login-password-error' : undefined} />
                <button type="button" className="icon-button" onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}>
                  {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {fields.password && <small id="login-password-error" className="field-error">{fields.password}</small>}
            </div>
            <button className="button button-primary login-submit" type="submit" disabled={busy}>
              {busy ? 'Signing in…' : 'Sign in'}
              {!busy && <ArrowRight size={18} />}
            </button>
          </form>
        </div>
        <p className="login-footer"><ShieldCheck size={16} />Workspace access is managed by your administrator.</p>
      </section>
      <aside className="login-art">
        <div className="radar-art" aria-hidden="true">
          <div className="radar-ring radar-ring-1" />
          <div className="radar-ring radar-ring-2" />
          <div className="radar-ring radar-ring-3" />
          <div className="radar-ring radar-ring-4" />
          <div className="radar-sweep" />
          <span className="radar-dot radar-dot-a" />
          <span className="radar-dot radar-dot-b" />
          <span className="radar-dot radar-dot-c" />
          <span className="radar-center"><Radar size={47} strokeWidth={1.4} /></span>
        </div>
        <div className="login-art-copy">
          <h2>Manage incoming enquiries</h2>
          <p>Review AI analysis, prepare customer replies, and keep follow-ups on track.</p>
        </div>
      </aside>
    </main>
  )
}
