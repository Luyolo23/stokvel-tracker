export type Role = 'CHAIR' | 'TREASURER' | 'MEMBER'
export type Status = 'DUE' | 'PENDING' | 'CONFIRMED' | 'REJECTED'

export interface Me {
  username: string
  displayName: string
  role: Role
  groupName: string
  monthlyAmountCents: number
}

export interface Contribution {
  id: number
  month: string
  dueDate: string
  amountDueCents: number
  status: Status
  overdue: boolean
  reference: string | null
  rejectReason: string | null
}

export interface MemberOverview {
  contributions: Contribution[]
  paidCents: number
  pendingCents: number
  owedCents: number
}

export interface Claim {
  id: number
  memberName: string
  month: string
  amountDueCents: number
  reference: string | null
  claimedAt: string | null
  decidedBy: string | null
  decidedAt: string | null
}

export interface OverdueMember {
  userId: number
  memberName: string
  phone: string | null
  months: string[]
  totalCents: number
}

export interface MemberRow {
  memberName: string
  confirmedCents: number
  owedCents: number
  overdueCount: number
}

export interface ChairSummary {
  month: string
  expectedThisMonthCents: number
  collectedThisMonthCents: number
  owedTotalCents: number
  pendingCount: number
  membersBehind: number
  members: MemberRow[]
}

export interface AuditEntry {
  id: number
  actor: string
  action: string
  detail: string
  createdAt: string
}
