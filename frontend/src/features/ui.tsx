import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { AlertCircle, ArrowLeft, ArrowRight, LoaderCircle } from 'lucide-react'
import { api, errorMessage } from '../lib/api'
import './features.css'

type RemoteResult<T> = { path: string; data?: T; error?: string; pending: boolean }

export function useRemote<T>(path: string | null) {
  const [result, setResult] = useState<RemoteResult<T>>()
  const [revision, setRevision] = useState(0)

  useEffect(() => {
    if (!path) return
    const controller = new AbortController()
    setResult(previous => ({
      path,
      data: previous?.path === path ? previous.data : undefined,
      pending: true,
    }))
    api<T>(path, { signal: controller.signal })
      .then(data => {
        if (!controller.signal.aborted) setResult({ path, data, pending: false })
      })
      .catch(error => {
        if (!controller.signal.aborted) {
          setResult(previous => ({ path, data: previous?.path === path ? previous.data : undefined, error: errorMessage(error), pending: false }))
        }
      })
    return () => controller.abort()
  }, [path, revision])

  const current = result?.path === path ? result : undefined
  return {
    data: current?.data,
    error: current?.error,
    pending: path !== null && (current?.pending ?? true),
    refresh: () => setRevision(current => current + 1),
  }
}

export function leadInitials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map(part => part.charAt(0)).join('').toUpperCase()
}

export function formatDate(date: string, withTime = false) {
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    ...(withTime ? { hour: 'numeric', minute: '2-digit' } : {}),
  }).format(new Date(date))
}

const labels: Record<string, string> = { PENDING_REVIEW: 'Pending review', AI: 'AI' }

export function labelFor(value: string) {
  return labels[value] ?? value.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase())
}

export function Badge({ value }: { value: string }) {
  return <span className={`feature-badge feature-badge--${value.toLowerCase()}`}>{labelFor(value)}</span>
}

export function Loading({ children = 'Loading…' }: { children?: ReactNode }) {
  return <div className="feature-loading" role="status"><LoaderCircle className="feature-spin" size={18} />{children}</div>
}

export function ErrorMessage({ children, retry }: { children: ReactNode; retry?: () => void }) {
  return (
    <div className="feature-error" role="alert">
      <AlertCircle size={17} aria-hidden="true" /><span>{children}</span>
      {retry && <button className="feature-text-button" type="button" onClick={retry}>Retry</button>}
    </div>
  )
}

export function EmptyState({ icon, title, children }: { icon?: ReactNode; title: string; children?: ReactNode }) {
  return <div className="feature-empty">{icon && <span className="feature-empty-icon">{icon}</span>}<strong>{title}</strong>{children && <p>{children}</p>}</div>
}

export function Pagination({ page, size, totalElements, totalPages, changePage }: {
  page: number
  size: number
  totalElements: number
  totalPages: number
  changePage: (page: number) => void
}) {
  if (totalElements === 0) return null
  return (
    <div className="feature-pagination">
      <span>{page * size + 1}–{Math.min((page + 1) * size, totalElements)} of {totalElements}</span>
      <div>
        <button type="button" className="button button-secondary" disabled={page === 0} onClick={() => changePage(page - 1)} aria-label="Previous page"><ArrowLeft size={15} aria-hidden="true" /></button>
        <span>Page {page + 1} of {totalPages}</span>
        <button type="button" className="button button-secondary" disabled={page >= totalPages - 1} onClick={() => changePage(page + 1)} aria-label="Next page"><ArrowRight size={15} aria-hidden="true" /></button>
      </div>
    </div>
  )
}
