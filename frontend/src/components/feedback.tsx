import { CircleAlert, CircleCheck, LoaderCircle, X } from 'lucide-react'
import { createContext, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

type ToastKind = 'success' | 'error'
type ToastEntry = { id: number; message: string; kind: ToastKind }
type Notify = (message: string, kind?: ToastKind) => void

const ToastContext = createContext<Notify | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [entries, setEntries] = useState<ToastEntry[]>([])
  const nextId = useRef(0)
  const timers = useRef(new Map<number, number>())

  function dismiss(id: number) {
    window.clearTimeout(timers.current.get(id))
    timers.current.delete(id)
    setEntries(current => current.filter(entry => entry.id !== id))
  }

  function toast(message: string, kind: ToastKind = 'success') {
    const id = nextId.current++
    setEntries(current => [...current, { id, message, kind }])
    timers.current.set(id, window.setTimeout(() => dismiss(id), 5000))
  }

  useEffect(() => {
    const activeTimers = timers.current
    return () => activeTimers.forEach(timer => window.clearTimeout(timer))
  }, [])

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div className="toasts" role="region" aria-label="Notifications">
        {entries.map(({ id, message, kind }) => (
          <div className={`toast toast-${kind}`} key={id}>
            {kind === 'success' ? <CircleCheck size={20} /> : <CircleAlert size={20} />}
            <span role={kind === 'error' ? 'alert' : 'status'}>{message}</span>
            <button type="button" className="icon-button" onClick={() => dismiss(id)} aria-label="Dismiss notification">
              <X size={17} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const toast = useContext(ToastContext)
  if (!toast) throw new Error('useToast requires ToastProvider')
  return { toast }
}

export function Loading({ message = 'Loading…' }: { message?: string }) {
  return <div className="loading-state" role="status"><LoaderCircle size={20} className="spin" />{message}</div>
}

export function InlineError({ message, retry }: { message: string; retry?: () => void }) {
  return (
    <div className="inline-error" role="alert">
      <CircleAlert size={18} />
      <span>{message}</span>
      {retry && <button className="button button-secondary button-small" type="button" onClick={retry}>Try again</button>}
    </div>
  )
}
