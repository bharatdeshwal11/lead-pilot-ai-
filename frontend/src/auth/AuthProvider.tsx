import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiError, errorMessage, SESSION_EXPIRED } from '../lib/api'

export type Role = 'ADMIN' | 'SALES_AGENT'
export type User = { id: number; name: string; email: string; role: Role; active: boolean }

interface AuthValue {
  user: User | null
  loading: boolean
  startupError: string
  checkSession: () => Promise<void>
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [startupError, setStartupError] = useState('')

  async function checkSession(signal?: AbortSignal) {
    setLoading(true)
    setStartupError('')
    try {
      const account = await api<User>('/api/auth/me', { signal })
      if (!signal?.aborted) setUser(account)
    } catch (error) {
      if (signal?.aborted) return
      setUser(null)
      if (!(error instanceof ApiError && error.status === 401)) setStartupError(errorMessage(error))
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    const expire = () => setUser(null)
    window.addEventListener(SESSION_EXPIRED, expire)
    void checkSession(controller.signal)
    return () => {
      controller.abort()
      window.removeEventListener(SESSION_EXPIRED, expire)
    }
  }, [])

  async function signIn(email: string, password: string) {
    const account = await api<User>('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    setStartupError('')
    setUser(account)
  }

  async function signOut() {
    try {
      await api<void>('/api/auth/logout', { method: 'POST' })
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) throw error
    }
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, loading, startupError, checkSession, signIn, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth requires AuthProvider')
  return value
}

export function roleName(role: Role) {
  return role === 'ADMIN' ? 'Administrator' : 'Sales agent'
}

export function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase()
}
