import type { Contribution } from '../types'

const LABELS: Record<Contribution['status'], string> = {
  DUE: 'Due',
  PENDING: 'Awaiting confirmation',
  CONFIRMED: 'Paid',
  REJECTED: 'Rejected',
}

export default function StatusBadge({ c }: { c: Pick<Contribution, 'status' | 'overdue'> }) {
  const rejected = c.status === 'REJECTED'
  const cls = rejected
    ? 'badge rejected'
    : c.overdue
      ? 'badge overdue'
      : `badge ${c.status.toLowerCase()}`
  return <span className={cls}>{rejected ? 'Rejected' : c.overdue ? 'Overdue' : LABELS[c.status]}</span>
}
