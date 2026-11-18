export const sources = ['WEBSITE', 'EMAIL', 'MANUAL', 'OTHER'] as const
export const statuses = ['NEW', 'CONTACTED', 'QUALIFIED', 'WON', 'LOST'] as const
export const priorities = ['HIGH', 'MEDIUM', 'LOW'] as const

export type Source = typeof sources[number]
export type LeadStatus = typeof statuses[number]
export type Priority = typeof priorities[number]

export type Assignee = { id: number; name: string }

export interface Lead {
  id: number
  customerName: string
  email: string
  phone: string | null
  enquiryMessage: string
  source: Source
  status: LeadStatus
  priority: Priority
  assignedUser: Assignee | null
  createdAt: string
  updatedAt: string
}

export interface LeadPage {
  content: Lead[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface Analysis {
  id: number
  leadId: number
  intent: Priority
  requirement: string | null
  budget: string | null
  timeline: string | null
  summary: string
  nextAction: string
  recommendedPriority: Priority
  uncertaintyNotes: string | null
  createdAt: string
  provider: string
}

export type DraftStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'

export interface ReplyDraft {
  id: number
  leadId: number
  body: string
  status: DraftStatus
  createdAt: string
  updatedAt: string
  provider: string
}

export interface Activity {
  id: number
  type: string
  description: string
  actorName: string | null
  createdAt: string
}
