import { Pencil, Plus, UsersRound, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { initials, roleName, useAuth } from '../auth/AuthProvider'
import type { Role, User } from '../auth/AuthProvider'
import { InlineError, Loading, useToast } from '../components/feedback'
import { api, ApiError, errorMessage } from '../lib/api'

type FormState = { name: string; email: string; role: Role; password: string; active: boolean }

const freshForm: FormState = { name: '', email: '', role: 'SALES_AGENT', password: '', active: true }

export default function UsersPage() {
  const { checkSession, user } = useAuth()
  const { toast } = useToast()
  const [users, setUsers] = useState<User[] | null>(null)
  const [loadError, setLoadError] = useState('')
  const [request, setRequest] = useState(0)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<User | null>(null)
  const [form, setForm] = useState<FormState>(freshForm)
  const [saveError, setSaveError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    void api<User[]>('/api/users', { signal: controller.signal })
      .then(setUsers)
      .catch(error => { if (!controller.signal.aborted) setLoadError(errorMessage(error)) })
    return () => controller.abort()
  }, [request])

  function openForm(account: User | null) {
    setEditing(account)
    setForm(account ? { name: account.name, email: account.email, role: account.role, active: account.active, password: '' } : freshForm)
    setFieldErrors({})
    setSaveError('')
    setShowForm(true)
  }

  function field<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm(current => ({ ...current, [key]: value }))
    setFieldErrors(current => ({ ...current, [key]: '' }))
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    if (editing?.active && !form.active && !window.confirm(`Deactivate ${editing.name}’s account?`)) return
    setBusy(true)
    setSaveError('')
    setFieldErrors({})
    const payload = editing
      ? { name: form.name.trim(), ...(editing.id !== user?.id && { role: form.role, active: form.active }), ...(form.password && { password: form.password }) }
      : { name: form.name.trim(), email: form.email.trim(), role: form.role, password: form.password }
    try {
      const saved = await api<User>(editing ? `/api/users/${editing.id}` : '/api/users', {
        method: editing ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      })
      setUsers(current => editing
        ? (current ?? []).map(account => account.id === saved.id ? saved : account)
        : [...(current ?? []), saved])
      setShowForm(false)
      toast(editing ? 'Team member updated.' : 'Team member added.')
      if (editing && editing.id === user?.id) void checkSession()
    } catch (error) {
      setSaveError(errorMessage(error))
      if (error instanceof ApiError) setFieldErrors(error.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page-stack">
      <header className="page-heading">
        <div><h1>Team members</h1><p>Manage account access and sales roles.</p></div>
        <button className="button button-primary" type="button" onClick={() => openForm(null)} disabled={!users || busy}>
          <Plus size={18} />Add member
        </button>
      </header>
      {showForm && (
        <section className="panel user-form-panel" aria-labelledby="user-form-title">
          <div className="panel-heading">
            <div><h2 id="user-form-title">{editing ? 'Edit team member' : 'Add team member'}</h2></div>
            <button className="icon-button" type="button" aria-label="Close team member form" onClick={() => setShowForm(false)} disabled={busy}>
              <X size={20} />
            </button>
          </div>
          {saveError && <InlineError message={saveError} />}
          <form className="user-form" onSubmit={save}>
            <div className="field">
              <label htmlFor="user-name">Full name</label>
              <input id="user-name" value={form.name} onChange={event => field('name', event.target.value)} required maxLength={120}
                autoComplete="name" aria-invalid={Boolean(fieldErrors.name)} aria-describedby={fieldErrors.name ? 'user-name-error' : undefined} />
              <FieldError id="user-name-error" value={fieldErrors.name} />
            </div>
            <div className="field">
              <label htmlFor="user-email">Email address</label>
              <input id="user-email" type="email" value={form.email} onChange={event => field('email', event.target.value)}
                required maxLength={254} readOnly={Boolean(editing)} autoComplete="email" aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={fieldErrors.email ? 'user-email-error' : undefined} />
              <FieldError id="user-email-error" value={fieldErrors.email} />
            </div>
            <div className="field">
              <label htmlFor="user-role">Role</label>
              <select id="user-role" value={form.role} disabled={editing?.id === user?.id}
                onChange={event => field('role', event.target.value as Role)} aria-invalid={Boolean(fieldErrors.role)}
                aria-describedby={fieldErrors.role ? 'user-role-error' : undefined}>
                <option value="SALES_AGENT">Sales agent</option><option value="ADMIN">Administrator</option>
              </select>
              <FieldError id="user-role-error" value={fieldErrors.role} />
            </div>
            <div className="field">
              <label htmlFor="user-password">{editing ? 'New password (optional)' : 'Temporary password'}</label>
              <input id="user-password" type="password" required={!editing} value={form.password} minLength={12} maxLength={72}
                onChange={event => field('password', event.target.value)} autoComplete="new-password" aria-invalid={Boolean(fieldErrors.password)}
                aria-describedby={fieldErrors.password ? 'user-password-error' : undefined} />
              <FieldError id="user-password-error" value={fieldErrors.password} />
            </div>
            {editing && (
              <div className="field">
                <label className="check-field">
                  <input type="checkbox" checked={form.active} disabled={editing.id === user?.id}
                    onChange={event => field('active', event.target.checked)} aria-invalid={Boolean(fieldErrors.active)}
                    aria-describedby={fieldErrors.active ? 'user-active-error' : undefined} />
                  Active account
                </label>
                <FieldError id="user-active-error" value={fieldErrors.active} />
              </div>
            )}
            <div className="user-form-actions">
              <button type="button" className="button button-secondary" onClick={() => setShowForm(false)} disabled={busy}>Cancel</button>
              <button type="submit" className="button button-primary" disabled={busy}>
                {busy ? 'Saving…' : editing ? 'Save changes' : 'Add team member'}
              </button>
            </div>
          </form>
        </section>
      )}
      <section className="panel user-directory" aria-label="Team directory">
        {loadError && <InlineError message={loadError} retry={() => { setLoadError(''); setRequest(i => i + 1) }} />}
        {!users && !loadError && <Loading message="Loading team members…" />}
        {users && (users.length > 0 ? <div className="table-scroll"><table className="directory-table">
          <thead><tr><th scope="col">Member</th><th scope="col">Role</th><th scope="col">Status</th><th scope="col"><span className="sr-only">Actions</span></th></tr></thead>
          <tbody>
            {users.map(account => (
              <tr key={account.id}>
                <td><div className="directory-member"><span className="avatar">{initials(account.name)}</span>
                  <div><strong>{account.name}</strong><small>{account.email}</small></div></div></td>
                <td>{roleName(account.role)}</td>
                <td><span className={`badge ${account.active ? 'badge-active' : 'badge-inactive'}`}>{account.active ? 'Active' : 'Inactive'}</span></td>
                <td><button type="button" className="icon-button" aria-label={`Edit ${account.name}`}
                  onClick={() => openForm(account)} disabled={busy}><Pencil size={17} /></button></td>
              </tr>
            ))}
          </tbody>
        </table></div> : <div className="empty-state"><UsersRound size={28} /><p>No team members to display.</p></div>)}
      </section>
    </div>
  )
}

function FieldError({ id, value }: { id: string; value?: string }) {
  return value ? <small id={id} className="field-error">{value}</small> : null
}
