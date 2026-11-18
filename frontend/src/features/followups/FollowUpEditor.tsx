import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { LoaderCircle, Search } from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'
import { useToast } from '../../components/feedback'
import { api, ApiError, errorMessage } from '../../lib/api'
import { ErrorMessage, useRemote } from '../ui'
import type { Assignee, Lead, LeadPage } from '../leads/types'
import type { FollowUp } from './types'

function localDateTime(value: string) {
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16)
}

export function FollowUpEditor({ lead, item, onSaved, onCancel }: {
  lead?: Lead
  item?: FollowUp
  onSaved: () => void
  onCancel: () => void
}) {
  const { user } = useAuth()
  const { toast } = useToast()
  const admin = user?.role === 'ADMIN'
  const assignees = useRemote<Assignee[]>(admin ? '/api/users/assignees' : null)
  const [selectedLead, setSelectedLead] = useState<Lead | undefined>(lead)
  const [assignedUserId, setAssignedUserId] = useState(item ? String(item.assigneeId) : '')
  const [note, setNote] = useState(item?.note ?? '')
  const initialDue = item ? localDateTime(item.dueAt) : ''
  const [due, setDue] = useState(initialDue)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const shouldChooseLead = !item && !lead
  const missingAssignee = item && assignees.data && !assignees.data.some(user => user.id === item.assigneeId)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    setError('')
    const errors: Record<string, string> = {}
    const dueDate = new Date(due)
    if (!item && !selectedLead) errors.leadId = 'Choose a lead.'
    if (!note.trim()) errors.note = 'Enter a follow-up note.'
    if (!due || Number.isNaN(dueDate.getTime())) errors.dueAt = 'Choose a due date and time.'
    if (admin && !item && selectedLead && !selectedLead.assignedUser && !assignedUserId) errors.assigneeId = 'Choose a salesperson for this unassigned lead.'
    setFieldErrors(errors)
    if (Object.keys(errors).length) {
      setError('Check the highlighted fields.')
      return
    }
    const data = {
      ...(item ? {} : { leadId: selectedLead?.id }),
      ...(admin && assignedUserId && assignedUserId !== String(item?.assigneeId) ? { assigneeId: Number(assignedUserId) } : {}),
      ...(!item || note.trim() !== item.note ? { note: note.trim() } : {}),
      ...(!item || due !== initialDue ? { dueAt: dueDate.toISOString() } : {}),
    }
    if (item && Object.keys(data).length === 0) {
      onCancel()
      return
    }
    setSaving(true)
    try {
      await api<FollowUp>(item ? `/api/follow-ups/${item.id}` : '/api/follow-ups', { method: item ? 'PATCH' : 'POST', body: JSON.stringify(data) })
      toast(item ? 'Follow-up updated.' : 'Follow-up scheduled.', 'success')
      onSaved()
    } catch (failure) {
      if (failure instanceof ApiError) setFieldErrors(failure.fieldErrors)
      setError(errorMessage(failure))
    } finally {
      setSaving(false)
    }
  }

  function fieldAccessibility(key: string) {
    return { 'aria-invalid': Boolean(fieldErrors[key]), 'aria-describedby': fieldErrors[key] ? `follow-up-error-${key}` : undefined }
  }

  function inlineError(key: string) {
    return fieldErrors[key] && <small id={`follow-up-error-${key}`} className="feature-field-error" aria-hidden="true">{fieldErrors[key]}</small>
  }

  return (
    <form className="feature-followup-editor" onSubmit={submit} noValidate aria-label={item ? 'Edit follow-up' : 'New follow-up'}>
      <h3>{item ? 'Edit follow-up' : 'New follow-up'}</h3>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {assignees.error && <ErrorMessage retry={assignees.refresh}>{assignees.error}</ErrorMessage>}
      <div className="feature-form-grid">
        {shouldChooseLead && <LeadPicker selected={selectedLead} onChange={setSelectedLead} fieldError={fieldErrors.leadId} />}
        <label className="feature-field">Due date and time
          <input type="datetime-local" required value={due} onChange={event => setDue(event.target.value)} {...fieldAccessibility('dueAt')} />
          {inlineError('dueAt')}
        </label>
        {admin && <label className="feature-field">Responsible salesperson
          <select value={assignedUserId} disabled={!assignees.data} onChange={event => setAssignedUserId(event.target.value)} {...fieldAccessibility('assigneeId')}>
            {!item && <option value="">{selectedLead?.assignedUser ? `Use lead assignee (${selectedLead.assignedUser.name})` : 'Select a salesperson'}</option>}
            {missingAssignee && <option value={item.assigneeId}>{item.assigneeName}</option>}
            {assignees.data?.map(assignee => <option key={assignee.id} value={assignee.id}>{assignee.name}</option>)}
          </select>
          {inlineError('assigneeId')}
        </label>}
        <label className="feature-field feature-field-wide">Follow-up note
          <textarea rows={3} required maxLength={500} value={note} onChange={event => setNote(event.target.value)} {...fieldAccessibility('note')} />
          {inlineError('note')}
        </label>
      </div>
      <p className="feature-assist">Due dates are shown in your local time.</p>
      <div className="feature-actions">
        <button type="button" className="button button-secondary" onClick={onCancel} disabled={saving}>Cancel</button>
        <button type="submit" className="button button-primary" disabled={saving}>
          {saving && <LoaderCircle size={16} className="feature-spin" aria-hidden="true" />} Save Follow-up
        </button>
      </div>
    </form>
  )
}

function LeadPicker({ selected, onChange, fieldError }: {
  selected?: Lead
  onChange: (lead: Lead | undefined) => void
  fieldError?: string
}) {
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  useEffect(() => {
    const timeout = window.setTimeout(() => setQuery(search.trim()), 300)
    return () => window.clearTimeout(timeout)
  }, [search])
  const params = new URLSearchParams({ size: '12', sort: 'createdAt,desc' })
  if (query) params.set('q', query)
  const suggestions = useRemote<LeadPage>(`/api/leads?${params}`)
  const matches = suggestions.data?.content ?? []
  const options = selected && !matches.some(lead => lead.id === selected.id) ? [selected, ...matches] : matches

  return (
    <>
      <label className="feature-field">
        Find a lead
        <span className="feature-search feature-lead-picker-search">
          <Search size={17} aria-hidden="true" />
          <input type="search" placeholder="Search leads by name or email" maxLength={200} value={search} onChange={event => setSearch(event.target.value)} />
        </span>
      </label>
      <label className="feature-field">Lead
        <select value={selected?.id ?? ''} required onChange={event => onChange(options.find(lead => lead.id === Number(event.target.value)))} aria-invalid={Boolean(fieldError)} aria-describedby={fieldError ? 'follow-up-error-leadId' : undefined}>
          <option value="">{suggestions.pending ? 'Finding leads…' : matches.length ? 'Select a lead' : 'No matching leads'}</option>
          {options.map(lead => <option key={lead.id} value={lead.id}>{lead.customerName} ({lead.email})</option>)}
        </select>
        {fieldError && <small id="follow-up-error-leadId" className="feature-field-error" aria-hidden="true">{fieldError}</small>}
      </label>
      {suggestions.error && <div className="feature-field-wide"><ErrorMessage retry={suggestions.refresh}>{suggestions.error}</ErrorMessage></div>}
    </>
  )
}
