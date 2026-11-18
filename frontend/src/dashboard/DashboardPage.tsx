import { ArrowDownRight, ArrowRight, CalendarClock, CircleCheckBig, Clock3, Flame, Inbox, Layers3, Plus, Target, TrendingUp } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { InlineError, Loading } from '../components/feedback'
import { api, errorMessage } from '../lib/api'

type LeadStatus = 'NEW' | 'CONTACTED' | 'QUALIFIED' | 'WON' | 'LOST'
type LeadSource = 'WEBSITE' | 'EMAIL' | 'MANUAL' | 'OTHER'
type Stats = {
  totalLeads: number
  newLeads: number
  qualifiedLeads: number
  wonLeads: number
  lostLeads: number
  highPriorityLeads: number
  overdueFollowUps: number
  conversionRate: number
  leadsBySource: Record<LeadSource, number>
  leadsByStatus: Record<LeadStatus, number>
}
type FollowUp = {
  id: number
  leadId: number
  leadName: string | null
  canOpenLead: boolean
  note: string
  dueAt: string
}
type FollowUps = { items: FollowUp[] }

const stageLabels = {
  NEW: 'New',
  CONTACTED: 'Contacted',
  QUALIFIED: 'Qualified',
  WON: 'Won',
  LOST: 'Lost',
} satisfies Record<LeadStatus, string>

const sourceLabels = {
  WEBSITE: { name: 'Website', color: '#6253e9' },
  EMAIL: { name: 'Email', color: '#9789fa' },
  MANUAL: { name: 'Manual', color: '#53bfae' },
  OTHER: { name: 'Other', color: '#f2b267' },
} satisfies Record<LeadSource, { name: string; color: string }>

const amount = new Intl.NumberFormat('en-IN')
const percent = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 1 })

export default function DashboardPage() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [statsError, setStatsError] = useState('')
  const [followUps, setFollowUps] = useState<FollowUp[] | null>(null)
  const [followUpError, setFollowUpError] = useState('')
  const [statsRequest, setStatsRequest] = useState(0)
  const [followUpRequest, setFollowUpRequest] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    void api<Stats>('/api/dashboard/stats', { signal: controller.signal })
      .then(setStats)
      .catch(error => { if (!controller.signal.aborted) setStatsError(errorMessage(error)) })
    return () => controller.abort()
  }, [statsRequest])

  useEffect(() => {
    const controller = new AbortController()
    void api<FollowUps>('/api/follow-ups?view=UPCOMING&size=4', { signal: controller.signal })
      .then(page => setFollowUps(page.items))
      .catch(error => { if (!controller.signal.aborted) setFollowUpError(errorMessage(error)) })
    return () => controller.abort()
  }, [followUpRequest])

  return (
    <div className="dashboard page-stack">
      <header className="page-heading dashboard-heading">
        <div>
          <h1>Overview</h1>
          <p>Track your pipeline and see which leads need attention.</p>
        </div>
        <Link className="button button-primary" to="/leads/new"><Plus size={18} />New lead</Link>
      </header>

      {statsError && <InlineError message={statsError} retry={() => { setStatsError(''); setStatsRequest(i => i + 1) }} />}
      {!stats && !statsError && <Loading message="Loading dashboard…" />}
      {stats && <>
        <section className="metrics-grid" aria-label="Lead totals">
          <Metric label="Total leads" value={stats.totalLeads} detail="Across all stages" icon={Layers3} tone="violet" />
          <Metric label="New leads" value={stats.newLeads} detail="Awaiting first contact" icon={Inbox} tone="blue" />
          <Metric label="Qualified leads" value={stats.qualifiedLeads} detail="Ready for the next step" icon={Target} tone="amber" />
          <Metric label="Won leads" value={stats.wonLeads} detail="Closed successfully" icon={CircleCheckBig} tone="green" />
        </section>

        <div className="analytics-grid">
          <PipelineChart values={stats.leadsByStatus} />
          <SourceChart values={stats.leadsBySource} total={stats.totalLeads} />
        </div>
      </>}

      <div className="dashboard-bottom">
        <section className="panel dashboard-panel">
          <div className="panel-heading">
            <div><h2>Upcoming follow-ups</h2><p>Scheduled customer touchpoints</p></div>
            <Link className="text-link" to="/follow-ups?view=UPCOMING">View all <ArrowRight size={15} /></Link>
          </div>
          {followUpError && <InlineError message={followUpError}
            retry={() => { setFollowUpError(''); setFollowUpRequest(i => i + 1) }} />}
          {!followUps && !followUpError && <Loading message="Loading follow-ups…" />}
          {followUps && (followUps.length > 0 ? (
            <ul className="dashboard-followups">
              {followUps.map(item => (
                <li key={item.id}>
                  <span className="dashboard-task-icon"><CalendarClock size={18} /></span>
                  <div className="dashboard-task-copy">
                    {item.canOpenLead
                      ? <Link to={`/leads/${item.leadId}`}>{item.leadName || `Lead #${item.leadId}`}</Link>
                      : <strong>{item.leadName || `Lead #${item.leadId}`}</strong>}
                    <span>{item.note}</span>
                  </div>
                  <time dateTime={item.dueAt}>{formatDue(item.dueAt)}</time>
                </li>
              ))}
            </ul>
          ) : <div className="empty-state dashboard-empty"><CalendarClock size={26} /><p>No upcoming follow-ups.</p></div>)}
        </section>

        {stats && (
          <section className="panel dashboard-panel attention-panel">
            <div className="panel-heading"><div><h2>Pipeline health</h2><p>Outcomes and items requiring action</p></div></div>
            <ul className="signals">
              <Signal label="Conversion rate" value={`${percent.format(stats.conversionRate)}%`} icon={TrendingUp} tone="green" />
              <Signal label="High-priority leads" value={amount.format(stats.highPriorityLeads)} icon={Flame} tone="amber" href="/leads?priority=HIGH" />
              <Signal label="Overdue follow-ups" value={amount.format(stats.overdueFollowUps)} icon={Clock3} tone="red" href="/follow-ups?view=OVERDUE" />
              <Signal label="Lost leads" value={amount.format(stats.lostLeads)} icon={ArrowDownRight} tone="gray" href="/leads?status=LOST" />
            </ul>
          </section>
        )}
      </div>
    </div>
  )
}

type Icon = typeof Layers3
type Tone = 'violet' | 'blue' | 'amber' | 'green' | 'red' | 'gray'

function Metric({ label, value, detail, icon: Icon, tone }: { label: string; value: number; detail: string; icon: Icon; tone: Tone }) {
  return (
    <article className="panel metric">
      <div className="metric-top">
        <span className={`metric-icon tone-${tone}`}><Icon size={21} /></span>
        <span className="metric-label">{label}</span>
      </div>
      <strong>{amount.format(value)}</strong>
      <span className="metric-detail">{detail}</span>
    </article>
  )
}

function PipelineChart({ values }: { values: Stats['leadsByStatus'] }) {
  const data = Object.entries(stageLabels).map(([key, name]) => ({ name, leads: values[key as LeadStatus] || 0 }))
  const hasLeads = data.some(item => item.leads > 0)
  return (
    <section className="panel dashboard-panel">
      <div className="panel-heading">
        <div><h2>Pipeline by status</h2><p>Lead count across each sales stage</p></div>
        <span className="chart-timeframe">All time</span>
      </div>
      {hasLeads ? (
        <>
          <div className="pipeline-chart" aria-hidden="true">
            <ResponsiveContainer width="100%" height="100%" minWidth={0}>
              <BarChart data={data} accessibilityLayer={false} margin={{ top: 12, right: 5, bottom: 0, left: -20 }}>
                <CartesianGrid vertical={false} stroke="#edf0f5" strokeDasharray="4 4" />
                <XAxis dataKey="name" tickLine={false} axisLine={false} tick={{ fill: '#637186', fontSize: 11 }} tickMargin={10} minTickGap={0} />
                <YAxis allowDecimals={false} tickLine={false} axisLine={false} tick={{ fill: '#637186', fontSize: 11 }} />
                <Tooltip cursor={{ fill: '#f3f2ff' }} contentStyle={{ borderRadius: 10, borderColor: '#e8e9f2' }} />
                <Bar name="Leads" dataKey="leads" fill="#6959eb" radius={[6, 6, 0, 0]} maxBarSize={48} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          </div>
          <ul className="sr-only" aria-label="Leads by status">
            {data.map(item => <li key={item.name}>{item.name} {amount.format(item.leads)}</li>)}
          </ul>
        </>
      ) : <div className="empty-state chart-empty"><Inbox size={27} /><p>As leads are added, their stages will appear here.</p></div>}
    </section>
  )
}

function SourceChart({ values, total }: { values: Stats['leadsBySource']; total: number }) {
  const data = Object.entries(sourceLabels).map(([key, source]) => ({
    ...source,
    fill: source.color,
    count: values[key as LeadSource] || 0,
  }))

  return (
    <section className="panel dashboard-panel">
      <div className="panel-heading"><div><h2>Lead sources</h2><p>Where enquiries come from</p></div></div>
      <div className="source-main">
        <div className="source-chart" aria-hidden="true">
          {total > 0 ? (
            <ResponsiveContainer width="100%" height="100%" minWidth={0}>
              <PieChart accessibilityLayer={false}><Pie data={data} dataKey="count" nameKey="name" stroke="#fff" strokeWidth={3}
                innerRadius="68%" outerRadius="94%" startAngle={90} endAngle={-270} isAnimationActive={false} /></PieChart>
            </ResponsiveContainer>
          ) : <div className="source-ring-empty" />}
          <div className="source-total"><strong>{amount.format(total)}</strong><span>Leads</span></div>
        </div>
        <ul className="source-legend" aria-label="Leads by source">
          {data.map(({ color, count, name }) => (
            <li key={name}><span aria-hidden="true" style={{ backgroundColor: color }} className="legend-dot" /><span>{name}</span><strong>{amount.format(count)}</strong></li>
          ))}
        </ul>
      </div>
    </section>
  )
}

function Signal({ label, value, icon: Icon, tone, href }: { label: string; value: string; icon: Icon; tone: Tone; href?: string }) {
  return (
    <li>
      <span className={`signal-icon tone-${tone}`}><Icon size={17} /></span>
      {href ? <Link to={href}>{label}</Link> : <span>{label}</span>}
      <strong>{value}</strong>
    </li>
  )
}

function formatDue(value: string) {
  const date = new Date(value)
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }).format(date)
}
