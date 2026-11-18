import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { ArrowUpRight, ListFilter, Plus, Search, UsersRound } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from '../../auth/AuthProvider'
import { Badge, EmptyState, ErrorMessage, formatDate, labelFor, leadInitials, Loading, Pagination, useRemote } from '../ui'
import { priorities, sources, statuses } from './types'
import type { Assignee, LeadPage } from './types'

const pageSize = 10
const defaultSort = 'createdAt,desc'
const sortOptions = [
  { value: defaultSort, label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
  { value: 'updatedAt,desc', label: 'Recently updated' },
  { value: 'customerName,asc', label: 'Name A–Z' },
  { value: 'priority,desc', label: 'Highest priority' },
]

export function LeadsPage() {
  const { user } = useAuth()
  const [query, setQuery] = useSearchParams()
  const [search, setSearch] = useState(query.get('q') ?? '')
  const q = query.get('q') ?? ''
  const page = Math.max(0, Number.parseInt(query.get('page') ?? '0', 10) || 0)
  const sort = query.get('sort') || defaultSort
  const status = query.get('status') || ''
  const source = query.get('source') || ''
  const priority = query.get('priority') || ''
  const assignedUserId = query.get('assignedUserId') || ''
  const filters = { status, source, priority, assignedUserId }
  const hasFilters = q !== '' || Object.values(filters).some(Boolean)
  const assignees = useRemote<Assignee[]>(user?.role === 'ADMIN' ? '/api/users/assignees' : null)
  const params = new URLSearchParams({ page: String(page), size: String(pageSize), sort })
  if (q) params.set('q', q)
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
  const leads = useRemote<LeadPage>(`/api/leads?${params}`)

  useEffect(() => setSearch(q), [q])

  function update(key: string, value: string) {
    setQuery(current => {
      const next = new URLSearchParams(current)
      if (key !== 'page') next.delete('page')
      if (value) next.set(key, value)
      else next.delete(key)
      return next
    })
  }

  function searchLeads(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    update('q', search.trim())
  }

  function clearFilters() {
    setSearch('')
    setQuery({})
  }

  return (
    <>
      <header className="feature-heading">
        <div>
          <h1>Lead Management</h1>
          <p>Find, organize and manage customer enquiries in one place.</p>
        </div>
        <Link className="button button-primary" to="/leads/new"><Plus size={17} aria-hidden="true" /> New Lead</Link>
      </header>
      <section className="panel feature-table-panel" aria-label="Leads">
        <div className="feature-table-top">
          <div><h2>All leads</h2>{leads.data && <p>{leads.data.totalElements} {leads.data.totalElements === 1 ? 'lead' : 'leads'}</p>}</div>
          <label className="feature-sort">
            <ListFilter size={16} aria-hidden="true" />
            <span className="feature-sr-only">Sort leads</span>
            <select value={sort} onChange={event => update('sort', event.target.value)}>
              {sortOptions.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
        </div>
        <div className="feature-filters">
          <form className="feature-search" onSubmit={searchLeads} role="search">
            <Search size={17} aria-hidden="true" />
            <input type="search" aria-label="Search leads" placeholder="Search name, email or enquiry" maxLength={200} value={search} onChange={event => setSearch(event.target.value)} />
            <button type="submit" className="feature-text-button">Search</button>
          </form>
          <select aria-label="Filter by status" value={status} onChange={event => update('status', event.target.value)}>
            <option value="">All statuses</option>
            {statuses.map(value => <option key={value} value={value}>{labelFor(value)}</option>)}
          </select>
          <select aria-label="Filter by priority" value={priority} onChange={event => update('priority', event.target.value)}>
            <option value="">All priorities</option>
            {priorities.map(value => <option key={value} value={value}>{labelFor(value)}</option>)}
          </select>
          <select aria-label="Filter by source" value={source} onChange={event => update('source', event.target.value)}>
            <option value="">All sources</option>
            {sources.map(value => <option key={value} value={value}>{labelFor(value)}</option>)}
          </select>
          {user?.role === 'ADMIN' && (
            <select aria-label="Filter by assignee" value={assignedUserId} onChange={event => update('assignedUserId', event.target.value)} disabled={!assignees.data}>
              <option value="">All assignees</option>
              {assignees.data?.map(assignee => <option key={assignee.id} value={assignee.id}>{assignee.name}</option>)}
            </select>
          )}
          {hasFilters && <button type="button" className="feature-text-button" onClick={clearFilters}>Clear filters</button>}
        </div>
        {assignees.error && <div className="feature-table-notice"><ErrorMessage retry={assignees.refresh}>{assignees.error}</ErrorMessage></div>}
        {leads.error && <div className="feature-table-notice"><ErrorMessage retry={leads.refresh}>{leads.error}</ErrorMessage></div>}
        {leads.pending && !leads.data && <Loading>Loading leads…</Loading>}
        {leads.data && (leads.data.content.length > 0 ? (
          <div className="feature-table-scroll" tabIndex={0} role="region" aria-label="Lead results table">
            <table className="feature-table">
              <thead><tr><th scope="col">Customer</th><th scope="col">Status</th><th scope="col">Priority</th><th scope="col" className="feature-table-secondary">Source</th><th scope="col" className="feature-table-secondary">Assigned to</th><th scope="col" className="feature-table-secondary">Created</th><th scope="col"><span className="feature-sr-only">Open</span></th></tr></thead>
              <tbody>
                {leads.data.content.map(lead => (
                  <tr key={lead.id}>
                    <td>
                      <Link className="feature-customer" to={`/leads/${lead.id}`}>
                        <span className={`feature-avatar feature-avatar--${lead.id % 4}`} aria-hidden="true">{leadInitials(lead.customerName)}</span>
                        <span><strong>{lead.customerName}</strong><small>{lead.email}</small></span>
                      </Link>
                    </td>
                    <td><Badge value={lead.status} /></td>
                    <td><Badge value={lead.priority} /></td>
                    <td className="feature-table-secondary">{labelFor(lead.source)}</td>
                    <td className="feature-table-secondary">{lead.assignedUser?.name ?? 'Unassigned'}</td>
                    <td className="feature-nowrap feature-table-secondary">{formatDate(lead.createdAt)}</td>
                    <td><Link className="feature-icon-link" to={`/leads/${lead.id}`} aria-label={`Open ${lead.customerName}`}><ArrowUpRight size={16} aria-hidden="true" /></Link></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState icon={<UsersRound size={23} aria-hidden="true" />} title={hasFilters ? 'No matching leads' : 'No leads yet'}>
            {hasFilters ? 'Try a different search or clear the filters.' : 'Create a lead to start tracking enquiries.'}
          </EmptyState>
        ))}
        {leads.data && leads.data.content.length > 0 && <Pagination {...leads.data} changePage={next => update('page', next ? String(next) : '')} />}
      </section>
    </>
  )
}
