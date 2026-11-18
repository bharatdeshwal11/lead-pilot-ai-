import { useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowLeft, ContactRound, LoaderCircle } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { useToast } from '../../components/feedback'
import { api, ApiError, errorMessage } from '../../lib/api'
import { ErrorMessage, labelFor, useRemote } from '../ui'
import { priorities, sources, statuses } from './types'
import type { Assignee, Lead, LeadStatus, Priority, Source } from './types'

interface LeadFormFields {
  customerName: string
  email: string
  phone: string
  enquiryMessage: string
  source: Source | ''
  status: LeadStatus
  priority: Priority
  assignedUserId: string
}

export function CreateLeadPage() {
  const navigate = useNavigate()

  return (
    <>
      <Link className="feature-back" to="/leads"><ArrowLeft size={16} aria-hidden="true" /> All leads</Link>
      <header className="feature-heading">
        <div><h1>Create lead</h1><p>Capture the customer's details and their original enquiry.</p></div>
      </header>
      <LeadForm onSaved={lead => navigate(`/leads/${lead.id}`)} />
    </>
  )
}

export function LeadForm({ lead, onSaved, onCancel }: {
  lead?: Lead
  onSaved: (result: Lead) => void
  onCancel?: () => void
}) {
  const { user } = useAuth()
  const { toast } = useToast()
  const isAdmin = user?.role === 'ADMIN'
  const assignees = useRemote<Assignee[]>(isAdmin ? '/api/users/assignees' : null)
  const [fields, setFields] = useState<LeadFormFields>({
    customerName: lead?.customerName ?? '',
    email: lead?.email ?? '',
    phone: lead?.phone ?? '',
    enquiryMessage: lead?.enquiryMessage ?? '',
    source: lead?.source ?? '',
    status: lead?.status ?? 'NEW',
    priority: lead?.priority ?? 'MEDIUM',
    assignedUserId: lead?.assignedUser ? String(lead.assignedUser.id) : '',
  })
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const assigneeMissing = lead?.assignedUser && assignees.data && !assignees.data.some(user => user.id === lead.assignedUser?.id)

  function edit<K extends keyof LeadFormFields>(key: K, value: LeadFormFields[K]) {
    setFields(previous => ({ ...previous, [key]: value }))
    setFieldErrors(previous => ({ ...previous, [key]: '' }))
  }

  function accessibility(key: keyof LeadFormFields) {
    return {
      'aria-invalid': Boolean(fieldErrors[key]),
      'aria-describedby': fieldErrors[key] ? `lead-error-${key}` : undefined,
    }
  }

  function fieldError(key: keyof LeadFormFields) {
    return fieldErrors[key] && <small className="feature-field-error" aria-hidden="true" id={`lead-error-${key}`}>{fieldErrors[key]}</small>
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (saving) return
    setFormError('')
    const cleaned = {
      customerName: fields.customerName.trim(),
      email: fields.email.trim(),
      phone: fields.phone.trim() || null,
      source: fields.source,
      enquiryMessage: fields.enquiryMessage.trim(),
      status: fields.status,
      priority: fields.priority,
    }
    const errors: Record<string, string> = {}
    if (!cleaned.customerName) errors.customerName = 'Enter the customer name.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleaned.email)) errors.email = 'Enter a valid email address.'
    if (cleaned.phone && (!/^[+\d ()-]{7,20}$/.test(cleaned.phone) || !/\d/.test(cleaned.phone))) {
      errors.phone = 'Use 7–20 characters with digits, spaces, +, ( ), or –.'
    }
    if (!cleaned.source) errors.source = 'Choose a lead source.'
    if (!cleaned.enquiryMessage) errors.enquiryMessage = 'Enter the customer enquiry.'
    setFieldErrors(errors)
    if (Object.keys(errors).length) {
      setFormError('Check the highlighted fields.')
      return
    }
    const originalAssignee = lead?.assignedUser ? String(lead.assignedUser.id) : ''
    const changes = lead
      ? Object.fromEntries(Object.entries(cleaned).filter(([key, value]) => value !== lead[key as keyof typeof cleaned]))
      : cleaned
    const payload = {
      ...changes,
      ...(isAdmin && fields.assignedUserId !== originalAssignee
        ? { assignedUserId: fields.assignedUserId ? Number(fields.assignedUserId) : null }
        : {}),
    }
    if (lead && Object.keys(payload).length === 0) {
      onSaved(lead)
      return
    }
    setSaving(true)
    try {
      const result = await api<Lead>(lead ? `/api/leads/${lead.id}` : '/api/leads', {
        method: lead ? 'PATCH' : 'POST',
        body: JSON.stringify(payload),
      })
      toast(lead ? 'Lead updated.' : 'Lead created.', 'success')
      onSaved(result)
    } catch (error) {
      if (error instanceof ApiError) setFieldErrors(error.fieldErrors)
      setFormError(errorMessage(error))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className="panel feature-form-panel" onSubmit={submit} noValidate aria-label={lead ? 'Edit lead' : 'Create lead'}>
      <div className="feature-form-header">
        <span className="feature-section-icon"><ContactRound size={20} aria-hidden="true" /></span>
        <div><h2>{lead ? 'Edit lead information' : 'Lead information'}</h2><p>Fields marked with an asterisk are required.</p></div>
      </div>
      {formError && <ErrorMessage>{formError}</ErrorMessage>}
      <div className="feature-form-grid">
        <label className="feature-field">Customer name <span aria-hidden="true">*</span>
          <input autoComplete="name" maxLength={120} required value={fields.customerName} onChange={event => edit('customerName', event.target.value)} {...accessibility('customerName')} />
          {fieldError('customerName')}
        </label>
        <label className="feature-field">Email address <span aria-hidden="true">*</span>
          <input type="email" autoComplete="email" maxLength={254} required value={fields.email} onChange={event => edit('email', event.target.value)} {...accessibility('email')} />
          {fieldError('email')}
        </label>
        <label className="feature-field">Phone number
          <input type="tel" autoComplete="tel" maxLength={20} placeholder="+91" value={fields.phone} onChange={event => edit('phone', event.target.value)} {...accessibility('phone')} />
          {fieldError('phone')}
        </label>
        <label className="feature-field">Source <span aria-hidden="true">*</span>
          <select required value={fields.source} onChange={event => edit('source', event.target.value as Source)} {...accessibility('source')}>
            <option value="">Select a source</option>
            {sources.map(value => <option key={value} value={value}>{labelFor(value)}</option>)}
          </select>
          {fieldError('source')}
        </label>
        <label className="feature-field feature-field-wide">Enquiry message <span aria-hidden="true">*</span>
          <textarea rows={5} required maxLength={4000} value={fields.enquiryMessage} onChange={event => edit('enquiryMessage', event.target.value)} {...accessibility('enquiryMessage')} />
          {fieldError('enquiryMessage')}
        </label>
        <label className="feature-field">Status
          <select value={fields.status} onChange={event => edit('status', event.target.value as LeadStatus)} {...accessibility('status')}>
            {statuses.map(value => <option key={value} value={value}>{labelFor(value)}</option>)}
          </select>
          {fieldError('status')}
        </label>
        <label className="feature-field">Priority
          <select value={fields.priority} onChange={event => edit('priority', event.target.value as Priority)} {...accessibility('priority')}>
            {priorities.map(value => <option key={value} value={value}>{labelFor(value)}</option>)}
          </select>
          {fieldError('priority')}
        </label>
        {isAdmin && <label className="feature-field">Assign to
          <select disabled={!assignees.data} value={fields.assignedUserId} onChange={event => edit('assignedUserId', event.target.value)} {...accessibility('assignedUserId')}>
            <option value="">Unassigned</option>
            {assigneeMissing && <option value={lead?.assignedUser?.id}>{lead?.assignedUser?.name}</option>}
            {assignees.data?.map(assignee => <option key={assignee.id} value={assignee.id}>{assignee.name}</option>)}
          </select>
          {fieldError('assignedUserId')}
        </label>}
        {assignees.error && <div className="feature-field-wide"><ErrorMessage retry={assignees.refresh}>{assignees.error}</ErrorMessage></div>}
      </div>
      <div className="feature-actions feature-form-actions">
        {onCancel ? <button className="button button-secondary" type="button" onClick={onCancel} disabled={saving}>Cancel</button> : <Link className="button button-secondary" to="/leads">Cancel</Link>}
        <button className="button button-primary" type="submit" disabled={saving}>
          {saving && <LoaderCircle size={16} className="feature-spin" aria-hidden="true" />}{lead ? 'Save Changes' : 'Create Lead'}
        </button>
      </div>
    </form>
  )
}
