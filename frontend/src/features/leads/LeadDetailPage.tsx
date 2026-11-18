import { useState } from 'react'
import { Activity as ActivityIcon, ArrowLeft, CalendarDays, FilePenLine, LayoutGrid, LoaderCircle, MessageSquareText, Pencil, Sparkles, Trash2, UserRound } from 'lucide-react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { useToast } from '../../components/feedback'
import { api, errorMessage } from '../../lib/api'
import { FollowUpsPanel } from '../followups/FollowUpsPage'
import { Badge, EmptyState, ErrorMessage, formatDate, labelFor, leadInitials, Loading, useRemote } from '../ui'
import { AnalysisPanel } from './AnalysisPanel'
import { LeadForm } from './LeadForm'
import { ReplyPanel } from './ReplyPanel'
import type { Activity, Analysis, Lead, Priority } from './types'

const sections = [
  { id: 'overview', label: 'Overview', icon: LayoutGrid },
  { id: 'analysis', label: 'AI Analysis', icon: Sparkles },
  { id: 'drafts', label: 'Reply Drafts', icon: FilePenLine },
  { id: 'follow-ups', label: 'Follow-ups', icon: CalendarDays },
  { id: 'activity', label: 'Activity', icon: ActivityIcon },
] as const

export function LeadDetailPage() {
  const { id } = useParams()
  if (!id || !/^\d+$/.test(id)) return <ErrorMessage>That lead could not be found.</ErrorMessage>
  return <LeadDetail key={id} id={id} />
}

function LeadDetail({ id }: { id: string }) {
  const { user } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()
  const [query, setQuery] = useSearchParams()
  const requested = query.get('section')
  const section = sections.find(item => item.id === requested)?.id ?? 'overview'
  const leadResult = useRemote<Lead>(`/api/leads/${id}`)
  const [updatedLead, setUpdatedLead] = useState<Lead>()
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState('')
  const analyses = useRemote<Analysis[]>(section === 'analysis' ? `/api/leads/${id}/analyses` : null)
  const [generatedAnalyses, setGeneratedAnalyses] = useState<Analysis[]>([])
  const [running, setRunning] = useState(false)
  const [analysisError, setAnalysisError] = useState('')
  const [applying, setApplying] = useState(false)
  const [applyError, setApplyError] = useState('')
  const lead = updatedLead ?? leadResult.data

  async function analyze() {
    setEditing(false)
    setQuery({ section: 'analysis' })
    setRunning(true)
    setAnalysisError('')
    setApplyError('')
    try {
      const result = await api<Analysis>(`/api/leads/${id}/analyze`, { method: 'POST' })
      setGeneratedAnalyses(current => [result, ...current.filter(analysis => analysis.id !== result.id)])
      toast('Lead analysis saved.', 'success')
    } catch (failure) {
      setAnalysisError(errorMessage(failure))
    } finally {
      setRunning(false)
    }
  }

  async function applyPriority(priority: Priority) {
    setApplying(true)
    setApplyError('')
    try {
      setUpdatedLead(await api<Lead>(`/api/leads/${id}`, { method: 'PATCH', body: JSON.stringify({ priority }) }))
      toast('Lead priority updated.', 'success')
    } catch (failure) {
      setApplyError(errorMessage(failure))
    } finally {
      setApplying(false)
    }
  }

  async function removeLead() {
    if (!lead || !window.confirm(`Delete ${lead.customerName}'s lead? This also deletes its activity, drafts and follow-ups.`)) return
    setDeleting(true)
    setDeleteError('')
    try {
      await api<void>(`/api/leads/${id}`, { method: 'DELETE' })
      toast('Lead deleted.', 'success')
      navigate('/leads')
    } catch (failure) {
      setDeleteError(errorMessage(failure))
      setDeleting(false)
    }
  }

  const orderedAnalyses = [
    ...generatedAnalyses,
    ...(analyses.data ?? []).filter(analysis => !generatedAnalyses.some(result => result.id === analysis.id)),
  ].sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt) || right.id - left.id)

  return (
    <>
      <Link className="feature-back" to="/leads"><ArrowLeft size={16} aria-hidden="true" /> All leads</Link>
      {leadResult.error && <ErrorMessage retry={leadResult.refresh}>{leadResult.error}</ErrorMessage>}
      {leadResult.pending && !lead && <Loading>Loading lead…</Loading>}
      {lead && (
        <>
          <header className="feature-heading feature-lead-heading">
            <div className="feature-lead-identity">
              <span className={`feature-avatar feature-avatar-large feature-avatar--${lead.id % 4}`} aria-hidden="true">{leadInitials(lead.customerName)}</span>
              <div>
                <h1>{lead.customerName}</h1>
                <div className="feature-lead-meta"><Badge value={lead.status} /><Badge value={lead.priority} /><span>Added {formatDate(lead.createdAt)}</span></div>
              </div>
            </div>
            <div className="feature-actions">
              <button type="button" className="button button-secondary" onClick={() => setEditing(true)} disabled={editing || deleting || applying}><Pencil size={16} aria-hidden="true" /> Edit Lead</button>
              <button type="button" className="button button-primary" onClick={analyze} disabled={running || deleting || editing}>
                {running ? <LoaderCircle size={16} className="feature-spin" aria-hidden="true" /> : <Sparkles size={16} aria-hidden="true" />}
                {running ? 'Analyzing…' : 'Analyze Lead'}
              </button>
            </div>
          </header>
          {editing ? <LeadForm lead={lead} onSaved={saved => { setUpdatedLead(saved); setEditing(false) }} onCancel={() => setEditing(false)} /> : (
            <>
              <nav className="feature-tabs" aria-label="Lead sections">
                {sections.map(({ id, label, icon: Icon }) => (
                  <Link key={id} to={id === 'overview' ? '?' : `?section=${id}`} aria-current={section === id ? 'page' : undefined}>
                    <Icon size={16} aria-hidden="true" /> {label}
                  </Link>
                ))}
              </nav>
              {section === 'overview' && <Overview lead={lead} removable={user?.role === 'ADMIN'} deleting={deleting} error={deleteError} onRemove={removeLead} />}
              {section === 'analysis' && <AnalysisPanel analyses={orderedAnalyses} pending={analyses.pending} loadError={analyses.error} onRetry={analyses.refresh} running={running} analysisError={analysisError} priority={lead.priority} applying={applying} applyError={applyError} onApply={applyPriority} />}
              {section === 'drafts' && <ReplyPanel leadId={lead.id} />}
              {section === 'follow-ups' && <FollowUpsPanel lead={lead} />}
              {section === 'activity' && <ActivityPanel leadId={lead.id} />}
            </>
          )}
        </>
      )}
    </>
  )
}

function Overview({ lead, removable, deleting, error, onRemove }: {
  lead: Lead; removable: boolean; deleting: boolean; error: string; onRemove: () => void
}) {
  return (
    <div className="feature-detail-grid">
      <section className="panel feature-detail-panel feature-enquiry-panel">
        <div className="feature-form-header">
          <span className="feature-section-icon"><MessageSquareText size={20} aria-hidden="true" /></span>
          <div><h2>Customer enquiry</h2><p>{labelFor(lead.source)} · {formatDate(lead.createdAt, true)}</p></div>
        </div>
        <p className="feature-enquiry-text">{lead.enquiryMessage}</p>
      </section>
      <section className="panel feature-detail-panel">
        <div className="feature-form-header">
          <span className="feature-section-icon"><UserRound size={20} aria-hidden="true" /></span>
          <div><h2>Lead details</h2><p>Contact information and assignment</p></div>
        </div>
        <dl className="feature-facts">
          <div><dt>Email</dt><dd><a href={`mailto:${lead.email}`}>{lead.email}</a></dd></div>
          <div><dt>Phone</dt><dd>{lead.phone ? <a href={`tel:${lead.phone}`}>{lead.phone}</a> : 'Unspecified'}</dd></div>
          <div><dt>Assigned to</dt><dd>{lead.assignedUser?.name ?? 'Unassigned'}</dd></div>
          <div><dt>Last updated</dt><dd>{formatDate(lead.updatedAt, true)}</dd></div>
        </dl>
      </section>
      {removable && <section className="panel feature-detail-panel feature-delete-panel">
        <div className="feature-section-head">
          <div><h2>Delete lead</h2><p>Permanently remove this lead and its related records.</p></div>
          <button type="button" className="button button-danger" onClick={onRemove} disabled={deleting}>
            {deleting ? <LoaderCircle size={16} className="feature-spin" aria-hidden="true" /> : <Trash2 size={16} aria-hidden="true" />} Delete Lead
          </button>
        </div>
        {error && <ErrorMessage>{error}</ErrorMessage>}
      </section>}
    </div>
  )
}

function ActivityPanel({ leadId }: { leadId: number }) {
  const activity = useRemote<Activity[]>(`/api/leads/${leadId}/activities`)
  return (
    <section className="panel feature-detail-panel">
      <div className="feature-section-head"><div><h2>Activity history</h2><p>Changes made to this lead and by whom.</p></div></div>
      {activity.error && <ErrorMessage retry={activity.refresh}>{activity.error}</ErrorMessage>}
      {activity.pending && !activity.data && <Loading>Loading activity…</Loading>}
      {activity.data?.length === 0 && <EmptyState title="No activity recorded" icon={<ActivityIcon size={22} aria-hidden="true" />} />}
      {activity.data && activity.data.length > 0 && (
        <ol className="feature-activity">
          {activity.data.map(event => (
            <li key={event.id}>
              <span className="feature-activity-icon"><ActivityIcon size={15} aria-hidden="true" /></span>
              <div><strong>{event.description}</strong><span>{event.actorName || 'System'} · {formatDate(event.createdAt, true)}</span></div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
