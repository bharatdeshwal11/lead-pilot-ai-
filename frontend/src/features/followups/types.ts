export const views = ['ALL', 'UPCOMING', 'OVERDUE', 'COMPLETED'] as const
export type FollowUpView = typeof views[number]

export interface FollowUp {
  id: number
  leadId: number
  leadName: string | null
  canOpenLead: boolean
  assigneeId: number
  assigneeName: string
  note: string
  dueAt: string
  completedAt: string | null
  createdAt: string
  updatedAt: string
  status: Exclude<FollowUpView, 'ALL'>
}

export interface FollowUpPage {
  items: FollowUp[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}
