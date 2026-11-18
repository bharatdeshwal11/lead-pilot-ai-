import { useState } from 'react'
import { Check, FilePenLine, LoaderCircle, Sparkles, X } from 'lucide-react'
import { useAuth } from '../../auth/AuthProvider'
import { useToast } from '../../components/feedback'
import { api, errorMessage } from '../../lib/api'
import { Badge, EmptyState, ErrorMessage, formatDate, labelFor, Loading, useRemote } from '../ui'
import type { ReplyDraft } from './types'

const maxDraftLength = 2500
type DraftAction = 'save' | 'submit' | 'approve' | 'reject' | 'reopen'

export function ReplyPanel({ leadId }: { leadId: number }) {
  const { toast } = useToast()
  const drafts = useRemote<ReplyDraft[]>(`/api/leads/${leadId}/reply-drafts`)
  const [updates, setUpdates] = useState<ReplyDraft[]>([])
  const [selectedId, setSelectedId] = useState<number>()
  const [generating, setGenerating] = useState(false)
  const [error, setError] = useState('')
  const knownDrafts = [...updates, ...(drafts.data ?? []).filter(draft => !updates.some(updated => updated.id === draft.id))]
    .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt) || right.id - left.id)
  const current = knownDrafts.find(draft => draft.id === selectedId) ?? knownDrafts[0]

  function updateDraft(updated: ReplyDraft) {
    setUpdates(previous => [updated, ...previous.filter(draft => draft.id !== updated.id)])
    setSelectedId(updated.id)
  }

  async function generate() {
    setGenerating(true)
    setError('')
    try {
      updateDraft(await api<ReplyDraft>(`/api/leads/${leadId}/reply-draft`, { method: 'POST' }))
      toast('Reply draft generated.', 'success')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setGenerating(false)
    }
  }

  return (
    <section className="panel feature-detail-panel">
      <div className="feature-section-head">
        <div>
          <h2>Reply Drafts</h2>
          <p>Prepare a customer reply for internal review. The application saves drafts for your team.</p>
        </div>
        <button type="button" className="button button-primary" onClick={generate} disabled={generating}>
          {generating ? <LoaderCircle size={16} className="feature-spin" aria-hidden="true" /> : <Sparkles size={16} aria-hidden="true" />}
          {generating ? 'Generating…' : 'Generate Draft'}
        </button>
      </div>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      {drafts.error && <ErrorMessage retry={drafts.refresh}>{drafts.error}</ErrorMessage>}
      {drafts.pending && knownDrafts.length === 0 && <Loading>Loading reply drafts…</Loading>}
      {!drafts.pending && !drafts.error && knownDrafts.length === 0 && (
        <EmptyState title="No reply drafts yet" icon={<FilePenLine size={22} aria-hidden="true" />}>
          Generate a reply using this lead’s original enquiry.
        </EmptyState>
      )}
      {current && (
        <>
          {knownDrafts.length > 1 && <label className="feature-field feature-draft-picker">Draft history
            <select value={current.id} onChange={event => setSelectedId(Number(event.target.value))}>
              {knownDrafts.map(draft => <option key={draft.id} value={draft.id}>{formatDate(draft.createdAt, true)} · {labelFor(draft.status)} · #{draft.id}</option>)}
            </select>
          </label>}
          <DraftEditor key={`${current.id}-${current.updatedAt}-${current.status}`} draft={current} onUpdated={updateDraft} />
        </>
      )}
    </section>
  )
}

function DraftEditor({ draft, onUpdated }: { draft: ReplyDraft; onUpdated: (draft: ReplyDraft) => void }) {
  const { user } = useAuth()
  const { toast } = useToast()
  const [body, setBody] = useState(draft.body)
  const [working, setWorking] = useState<DraftAction>()
  const [error, setError] = useState('')
  const editable = draft.status === 'DRAFT' || draft.status === 'PENDING_REVIEW'
  const adminReview = user?.role === 'ADMIN' && draft.status === 'PENDING_REVIEW'
  const dirty = body !== draft.body

  async function act(action: DraftAction) {
    setError('')
    if ((action === 'save' || action === 'submit') && !body.trim()) {
      setError('Enter a reply before saving.')
      return
    }
    setWorking(action)
    const path = `/api/reply-drafts/${draft.id}`
    const decision = action === 'approve' || action === 'reject'
    const payload = action === 'reopen' ? { status: 'DRAFT' } : {
      body: body.trim(),
      ...(action === 'submit' ? { status: 'PENDING_REVIEW' } : {}),
    }
    try {
      const updated = await api<ReplyDraft>(decision ? `${path}/${action}` : path, decision ? { method: 'POST' } : {
        method: 'PATCH',
        body: JSON.stringify(payload),
      })
      const messages: Record<DraftAction, string> = {
        approve: 'Draft approved.',
        reject: 'Draft rejected.',
        reopen: 'Draft reopened for editing.',
        save: 'Draft saved.',
        submit: 'Draft submitted for review.',
      }
      setBody(updated.body)
      onUpdated(updated)
      toast(messages[action], 'success')
    } catch (failure) {
      setError(errorMessage(failure))
    } finally {
      setWorking(undefined)
    }
  }

  return (
    <div className="feature-draft-editor">
      <div className="feature-draft-meta"><Badge value={draft.status} /><span>Updated {formatDate(draft.updatedAt, true)}</span></div>
      {error && <ErrorMessage>{error}</ErrorMessage>}
      <label className="feature-field">Reply body
        <textarea rows={7} maxLength={maxDraftLength} value={body} readOnly={!editable} disabled={Boolean(working)} onChange={event => setBody(event.target.value)} />
      </label>
      {editable && <p className="feature-assist">Confirm any details and commitments before approving this reply.</p>}
      {adminReview && dirty && <p className="feature-assist">Save your changes to review the updated draft.</p>}
      <div className="feature-actions">
        {working && <LoaderCircle size={17} className="feature-spin" role="status" aria-label="Updating draft" />}
        {editable && <button type="button" className="button button-secondary" disabled={Boolean(working) || !dirty} onClick={() => void act('save')}>Save Draft</button>}
        {draft.status === 'DRAFT' && <button type="button" className="button button-primary" disabled={Boolean(working)} onClick={() => void act('submit')}>Submit for Review</button>}
        {adminReview && <>
          <button type="button" className="button button-secondary" disabled={Boolean(working) || dirty} onClick={() => void act('reject')}><X size={15} aria-hidden="true" /> Reject Draft</button>
          <button type="button" className="button button-primary" disabled={Boolean(working) || dirty} onClick={() => void act('approve')}><Check size={15} aria-hidden="true" /> Approve Draft</button>
        </>}
        {draft.status === 'REJECTED' && <button type="button" className="button button-secondary" disabled={Boolean(working)} onClick={() => void act('reopen')}>Reopen Draft</button>}
      </div>
    </div>
  )
}
