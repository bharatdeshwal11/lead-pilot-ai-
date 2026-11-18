import { lazy } from 'react'
import { Navigate, Outlet, Route, Routes, Link, useLocation } from 'react-router-dom'
import { useAuth } from './auth/AuthProvider'
import LoginPage from './auth/LoginPage'
import AppShell from './components/AppShell'
import { Loading } from './components/feedback'
const DashboardPage = lazy(() => import('./dashboard/DashboardPage'))
const LeadsPage = lazy(() => import('./features/leads/LeadsPage').then(module => ({ default: module.LeadsPage })))
const CreateLeadPage = lazy(() => import('./features/leads/LeadForm').then(module => ({ default: module.CreateLeadPage })))
const LeadDetailPage = lazy(() => import('./features/leads/LeadDetailPage').then(module => ({ default: module.LeadDetailPage })))
const FollowUpsPage = lazy(() => import('./features/followups/FollowUpsPage').then(module => ({ default: module.FollowUpsPage })))
const UsersPage = lazy(() => import('./users/UsersPage'))

export default function App() {
  const { loading } = useAuth()

  if (loading) return <div className="app-loading"><Loading message="Opening workspace…" /></div>

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireSession />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="leads" element={<LeadsPage />} />
          <Route path="leads/new" element={<CreateLeadPage />} />
          <Route path="leads/:id" element={<LeadDetailPage />} />
          <Route path="follow-ups" element={<FollowUpsPage />} />
          <Route path="users" element={<RequireAdmin />} />
          <Route path="*" element={<MissingPage />} />
        </Route>
      </Route>
    </Routes>
  )
}

function RequireSession() {
  const { user } = useAuth()
  const { pathname, search } = useLocation()
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: pathname + search }} />
}

function RequireAdmin() {
  const { user } = useAuth()
  return user?.role === 'ADMIN' ? <UsersPage /> : (
    <div className="panel empty-state permission-page">
      <h1>Access restricted</h1>
      <p>Only administrators can manage team members.</p>
      <Link className="button button-secondary" to="/">Back to overview</Link>
    </div>
  )
}

function MissingPage() {
  return (
    <div className="panel empty-state permission-page">
      <h1>Page unavailable</h1>
      <p>Check the address or return to your workspace.</p>
      <Link className="button button-secondary" to="/">Back to overview</Link>
    </div>
  )
}
