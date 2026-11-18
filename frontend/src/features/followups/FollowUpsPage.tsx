import { useState } from 'react'
import { CalendarCheck, CalendarDays, Check, LoaderCircle, Pencil, Plus } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { useToast } from '../../components/feedback'
import { api, errorMessage } from '../../lib/api'
import { Badge, EmptyState, ErrorMessage, formatDate, labelFor, Loading, Pagination, useRemote } from '../ui'
import type { Lead } from '../leads/types'
import { FollowUpEditor } from './FollowUpEditor'
import type { FollowUp, FollowUpPage, FollowUpView } from './types'
import { views } from './types'

const pageSize = 10

export function FollowUpsPage() {
  const [query, setQuery] = useSearchParams()
  const view = views.find(value => value === query.get('view')) ?? 'ALL'
  function changeView(next: FollowUpView) {
    setQuery(next === 'ALL' ? {} : { view: next })
  }
  return (
    <>
      <header className="feature-heading">
        <div><h1>Follow-ups</h1><p>Schedule customer touchpoints and keep track of what is due.</p></div>
      </header>
      <FollowUpsPanel view={view} onChangeView={changeView} />
    </>
  )
}

export function FollowUpsPanel({ lead, view, onChangeView }: {
  lead?: Lead
  view?: FollowUpView
  onChangeView?: (view: FollowUpView) => void
}) {
  const { toast } = useToast()
  const [localView, setLocalView] = useState<FollowUpView>('ALL')
  const [page, setPage] = useState(0)
  const [editor, setEditor] = useState<FollowUp | 'new'>()
  const [completing, setCompleting] = useState<number>()
  const [error, setError] = useState('')
  const activeView = view ?? localView
  const params = new URLSearchParams({ view: activeView, page: String(page), size: String(pageSize) })
  if (lead) params.set('leadId', String(lead.id))
  const followUps = useRemote<FollowUpPage>(`/api/follow-ups?${params}`)

  function filter(next: FollowUpView) {
    setPage(0)
    if (onChangeView) onChangeView(next)
    else setLocalView(next)
  }

  function refreshList() {
    setPage(0)
    followUps.refresh()
  }

  async function complete(item: FollowUp) {
    setCompleting(item.id)
    setError('')
    try {
      await api(`/api/follow-ups/${item.id}`, { method: 'PATCH', body: JSON.stringify({ completed: true }) })
      refreshList()
      toast('Follow-up completed.', 'success')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setCompleting(undefined)
    }
  }

  return (
    <section className="panel feature-detail-panel feature-followups">
      <div className="feature-section-head">
        <div><h2>{lead ? 'Lead follow-ups' : 'Follow-up schedule'}</h2><p>Manage upcoming, overdue and completed follow-ups.</p></div>
        <button type="button" className="button button-primary" disabled={Boolean(editor)} onClick={() => setEditor('new')}>
          <Plus size={17} aria-hidden="true" /> Schedule Follow-up
        </button>
      </div>
      {editor && <FollowUpEditor key={editor === 'new' ? 'new' : editor.id} lead={lead} item={editor === 'new' ? undefined : editor} onCancel={() => setEditor(undefined)} onSaved={() => {
        setEditor(undefined)
        refreshList()
      }} />}
      <div className="feature-segments" aria-label="Follow-up view">
        {views.map(value => <button key={value} type="button" aria-pressed={activeView === value} onClick={() => filter(value)}>{value === 'ALL' ? 'All follow-ups' : labelFor(value)}</button>)}
      </div>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {followUps.error && <ErrorMessage retry={followUps.refresh}>{followUps.error}</ErrorMessage>}
      {followUps.pending && !followUps.data && <Loading>Loading follow-ups…</Loading>}
      {followUps.data?.items.length === 0 && (
        <EmptyState icon={<CalendarDays size={23} aria-hidden="true" />} title={activeView === 'ALL' ? 'No follow-ups yet' : `No ${activeView.toLowerCase()} follow-ups`}>
          {activeView === 'ALL' ? 'Schedule a follow-up to keep a customer enquiry moving.' : 'Select another view to see more follow-ups.'}
        </EmptyState>
      )}
      {followUps.data && followUps.data.items.length > 0 && (
        <>
          <div className="feature-followup-list">
            {followUps.data.items.map(item => (
              <article className="feature-followup-row" key={item.id}>
                <span className="feature-section-icon feature-followup-icon"><CalendarCheck size={19} aria-hidden="true" /></span>
                <div className="feature-followup-content">
                  <div className="feature-followup-title">
                    <strong>{item.note}</strong><Badge value={item.status} />
                  </div>
                  <div className="feature-followup-meta">
                    {!lead && (item.canOpenLead ? <Link to={`/leads/${item.leadId}`}>{item.leadName ?? `Lead #${item.leadId}`}</Link> : <span>Lead #{item.leadId}</span>)}
                    <span>{item.assigneeName}</span>
                    <span>Due <time dateTime={item.dueAt}>{formatDate(item.dueAt, true)}</time></span>
                    {item.completedAt && <span>Completed <time dateTime={item.completedAt}>{formatDate(item.completedAt, true)}</time></span>}
                  </div>
                </div>
                {item.status !== 'COMPLETED' && <div className="feature-actions">
                  <button type="button" className="button button-secondary" onClick={() => setEditor(item)} disabled={Boolean(editor) || Boolean(completing)}><Pencil size={15} aria-hidden="true" /> Edit</button>
                  <button type="button" className="button button-secondary" onClick={() => void complete(item)} disabled={Boolean(editor) || Boolean(completing)}>
                    {completing === item.id ? <LoaderCircle size={15} className="feature-spin" aria-hidden="true" /> : <Check size={15} aria-hidden="true" />} Mark complete
                  </button>
                </div>}
              </article>
            ))}
          </div>
          <Pagination {...followUps.data} changePage={setPage} />
        </>
      )}
    </section>
  )
}
