import { CalendarCheck, CalendarDays, ChevronRight, LayoutDashboard, LogOut, Menu, UsersRound, UserRound, X } from 'lucide-react'
import { Suspense, useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { initials, roleName, useAuth } from '../auth/AuthProvider'
import { errorMessage } from '../lib/api'
import { Brand } from './Brand'
import { Loading, useToast } from './feedback'

const links = [
  { to: '/', name: 'Overview', icon: LayoutDashboard },
  { to: '/leads', name: 'All leads', icon: UsersRound },
  { to: '/follow-ups', name: 'Follow-ups', icon: CalendarCheck },
]

export default function AppShell() {
  const { user, signOut } = useAuth()
  const { toast } = useToast()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [signingOut, setSigningOut] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)
  const closeButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!menuOpen) return
    closeButton.current?.focus()
    function onEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') dismissMenu()
    }
    window.addEventListener('keydown', onEscape)
    return () => window.removeEventListener('keydown', onEscape)
  }, [menuOpen])

  if (!user) return null

  function dismissMenu() {
    setMenuOpen(false)
    menuButton.current?.focus()
  }

  async function leave() {
    setSigningOut(true)
    try {
      await signOut()
    } catch (error) {
      toast(errorMessage(error), 'error')
      setSigningOut(false)
    }
  }

  const path = location.pathname
  const pageName = path === '/leads/new' ? 'New lead'
    : path.startsWith('/leads/') ? 'Lead details'
    : path === '/users' ? 'Team members'
    : links.find(link => link.to === path)?.name || 'Page'
  const date = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date())

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">Skip to content</a>
      {menuOpen && <button className="mobile-backdrop" type="button" onClick={dismissMenu} aria-label="Close navigation" tabIndex={-1} />}
      <aside className={`sidebar ${menuOpen ? 'sidebar-open' : ''}`} id="primary-navigation">
        <div className="sidebar-top">
          <Brand />
          <button ref={closeButton} type="button" className="icon-button sidebar-close" onClick={dismissMenu} aria-label="Close menu"><X size={23} /></button>
        </div>
        <nav className="sidebar-nav" aria-label="Main navigation">
          <p className="nav-label">Workspace</p>
          {links.map(({ to, name, icon: Icon }) => (
            <NavLink end={to === '/'} to={to} className="nav-item" onClick={() => setMenuOpen(false)} key={to}>
              <Icon size={20} />{name}
            </NavLink>
          ))}
          {user.role === 'ADMIN' && <>
            <p className="nav-label admin-label">Administration</p>
            <NavLink to="/users" className="nav-item" onClick={() => setMenuOpen(false)}>
              <UserRound size={20} />Team members
            </NavLink>
          </>}
        </nav>
        <div className="sidebar-bottom">
          <div className="profile">
            <span className="avatar">{initials(user.name)}</span>
            <div><strong title={user.name}>{user.name}</strong><small>{roleName(user.role)}</small></div>
          </div>
          <button className="icon-button sign-out" type="button" aria-label="Sign out" title="Sign out" onClick={() => void leave()} disabled={signingOut}>
            <LogOut size={19} />
          </button>
        </div>
      </aside>
      <div className="app-body">
        <header className="app-topbar">
          <div className="topbar-left">
            <button ref={menuButton} className="icon-button menu-trigger" type="button" onClick={() => setMenuOpen(open => !open)}
              aria-label="Open menu" aria-expanded={menuOpen} aria-controls="primary-navigation"><Menu size={24} /></button>
            <div className="breadcrumbs"><span>Workspace</span><ChevronRight size={13} /><strong>{pageName}</strong></div>
          </div>
          <p className="topbar-date"><CalendarDays size={17} />{date}</p>
        </header>
        <main className="app-main" id="main-content" tabIndex={-1}>
          <Suspense fallback={<Loading message="Opening page…" />}><Outlet /></Suspense>
        </main>
      </div>
    </div>
  )
}
