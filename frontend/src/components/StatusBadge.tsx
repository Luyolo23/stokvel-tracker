import type { Contribution } from '../types'

const LABELS: Record<Contribution['status'], string> = {
  DUE: 'Due',
  PENDING: 'Awaiting confirmation',
  CONFIRMED: 'Paid',
  REJECTED: 'Rejected',
}

export default function StatusBadge({ c }: { c: Pick<Contribution, 'status' | 'overdue'> }) {
  const cls = c.overdue ? 'badge overdue' : `badge ${c.status.toLowerCase()}`
  return <span className={cls}>{c.overdue ? 'Overdue' : LABELS[c.status]}</span>
}
