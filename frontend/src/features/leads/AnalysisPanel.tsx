import { ArrowUpRight, BrainCircuit, LoaderCircle } from 'lucide-react'
import { Badge, EmptyState, ErrorMessage, formatDate, Loading } from '../ui'
import type { Analysis, Priority } from './types'

export function AnalysisPanel({ analyses, pending, loadError, onRetry, running, analysisError, priority, applying, applyError, onApply }: {
  analyses: Analysis[]
  pending: boolean
  loadError?: string
  onRetry: () => void
  running: boolean
  analysisError: string
  priority: Priority
  applying: boolean
  applyError: string
  onApply: (priority: Priority) => void
}) {
  const latest = analyses[0]
  return (
    <section className="panel feature-detail-panel">
      <div className="feature-section-head">
        <div><h2>AI Analysis</h2><p>Review the customer's intent, reported requirements and a suggested next action.</p></div>
      </div>
      {analysisError && <ErrorMessage>{analysisError}</ErrorMessage>}
      {loadError && <ErrorMessage retry={onRetry}>{loadError}</ErrorMessage>}
      {(running || (pending && !latest)) && <Loading>{running ? 'Analyzing enquiry…' : 'Loading analyses…'}</Loading>}
      {!latest && !pending && !running && !loadError && <EmptyState title="No analysis yet" icon={<BrainCircuit size={23} aria-hidden="true" />}>Select Analyze Lead to review this enquiry.</EmptyState>}
      {latest && <>
        <AnalysisResult analysis={latest} />
        <div className="feature-analysis-actions">
          <span>Current lead priority <Badge value={priority} /></span>
          {latest.recommendedPriority !== priority && (
            <button type="button" className="button button-secondary" disabled={applying} onClick={() => onApply(latest.recommendedPriority)}>
              {applying ? <LoaderCircle size={16} className="feature-spin" aria-hidden="true" /> : <ArrowUpRight size={16} aria-hidden="true" />}
              Apply recommended priority
            </button>
          )}
        </div>
        {applyError && <ErrorMessage>{applyError}</ErrorMessage>}
        {analyses.length > 1 && (
          <section className="feature-analysis-history" aria-label="Analysis history">
            <h3>Prior analyses</h3>
            {analyses.slice(1).map(analysis => (
              <details key={analysis.id}>
                <summary>{formatDate(analysis.createdAt, true)} <Badge value={analysis.intent} /></summary>
                <AnalysisResult analysis={analysis} />
              </details>
            ))}
          </section>
        )}
      </>}
    </section>
  )
}

function AnalysisResult({ analysis }: { analysis: Analysis }) {
  return (
    <article className="feature-analysis-result">
      <div className="feature-analysis-intent">
        <div><span>Intent</span><Badge value={analysis.intent} /></div>
        <div><span>Recommended priority</span><Badge value={analysis.recommendedPriority} /></div>
        <time dateTime={analysis.createdAt}>{formatDate(analysis.createdAt, true)}</time>
      </div>
      <dl className="feature-facts feature-analysis-facts">
        <div><dt>Requirement</dt><dd>{analysis.requirement ?? 'Unspecified'}</dd></div>
        <div><dt>Stated budget</dt><dd>{analysis.budget ?? 'Unspecified'}</dd></div>
        <div><dt>Timeline</dt><dd>{analysis.timeline ?? 'Unspecified'}</dd></div>
      </dl>
      <div className="feature-content-block"><h3>Enquiry summary</h3><p>{analysis.summary}</p></div>
      <div className="feature-content-block"><h3>Suggested next action</h3><p>{analysis.nextAction}</p></div>
      {analysis.uncertaintyNotes && <div className="feature-content-block"><h3>Uncertainty</h3><p>{analysis.uncertaintyNotes}</p></div>}
    </article>
  )
}
