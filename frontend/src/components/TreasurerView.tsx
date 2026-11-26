import { useState } from 'react'
import { api } from '../api'
import { dateTime, monthLabel, rand } from '../format'
import type { Claim, OverdueMember } from '../types'
import { useLoader } from './useLoader'

type Part = 'pending' | 'overdue' | 'confirmed'

export default function TreasurerView({ part }: { part: Part }) {
  if (part === 'overdue') return <Overdue />
  return <ClaimList mode={part} />
}

/** Pending claims (confirm / reject) or recently confirmed (reverse a mistake). */
function ClaimList({ mode }: { mode: 'pending' | 'confirmed' }) {
  const { data, error, loading, reload } = useLoader<Claim[]>(
    mode === 'pending' ? api.pending : api.confirmed,
  )
  const [actionId, setActionId] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  async function run(fn: () => Promise<void>) {
    setBusy(true)
    setMessage('')
    try {
      await fn()
      setActionId(null)
      setReason('')
      await reload()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <p className="muted">Loading…</p>
  if (error || !data) return <p className="error">{error || 'No data'}</p>
  if (data.length === 0) {
    return (
      <p className="muted center">
        {mode === 'pending' ? 'Nothing waiting for you. All caught up.' : 'No confirmed payments yet.'}
      </p>
    )
  }

  return (
    <>
      {message && <p className="error" role="alert">{message}</p>}
      <ul className="list">
        {data.map((c) => (
          <li key={c.id} className="card">
            <div className="row">
              <div>
                <strong>{c.memberName}</strong>
                <div className="muted small">{monthLabel(c.month)} · {rand(c.amountDueCents)}</div>
              </div>
              <span className="muted small">
                {mode === 'pending' ? dateTime(c.claimedAt) : dateTime(c.decidedAt)}
              </span>
            </div>
            <p className="small">Reference: <strong>{c.reference ?? 'none'}</strong></p>

            {actionId !== c.id && mode === 'pending' && (
              <div className="row gap">
                <button className="btn primary" disabled={busy} onClick={() => run(() => api.confirm(c.id))}>
                  Confirm
                </button>
                <button className="btn danger" disabled={busy} onClick={() => { setActionId(c.id); setMessage('') }}>
                  Reject
                </button>
              </div>
            )}
            {actionId !== c.id && mode === 'confirmed' && (
              <button className="btn ghost" onClick={() => { setActionId(c.id); setMessage('') }}>
                Reverse (correct a mistake)
              </button>
            )}

            {actionId === c.id && (
              <form
                className="inline-form"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(() => (mode === 'pending' ? api.reject(c.id, reason) : api.reverse(c.id, reason)))
                }}
              >
                <label>
                  {mode === 'pending' ? 'Reason for rejecting (the member will see this)' : 'Why is this being reversed?'}
                  <input value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} autoFocus required />
                </label>
                <div className="row gap">
                  <button className="btn danger" disabled={busy}>
                    {mode === 'pending' ? 'Reject payment' : 'Reverse payment'}
                  </button>
                  <button type="button" className="btn ghost" onClick={() => setActionId(null)}>Cancel</button>
                </div>
              </form>
            )}
          </li>
        ))}
      </ul>
    </>
  )
}

/** Members with unpaid months past their due date, each with a pre-written WhatsApp reminder. */
function Overdue() {
  const { data, error, loading } = useLoader<OverdueMember[]>(api.overdue)

  if (loading) return <p className="muted">Loading…</p>
  if (error || !data) return <p className="error">{error || 'No data'}</p>
  if (data.length === 0) return <p className="muted center">Nobody is overdue. 🎉</p>

  return (
    <ul className="list">
      {data.map((m) => {
        const first = m.memberName.split(' ')[0]
        const months = m.months.map(monthLabel).join(', ')
        const text =
          `Hi ${first}, friendly reminder from the stokvel: ${months} ` +
          `still outstanding (${rand(m.totalCents)}). Please pay and tap "I've paid" in the app. Thank you!`
        const link = m.phone ? `https://wa.me/${m.phone}?text=${encodeURIComponent(text)}` : null
        return (
          <li key={m.userId} className="card">
            <div className="row">
              <strong>{m.memberName}</strong>
              <strong>{rand(m.totalCents)}</strong>
            </div>
            <p className="muted small">{months}</p>
            {link ? (
              <a className="btn whatsapp" href={link} target="_blank" rel="noreferrer">
                Send WhatsApp reminder
              </a>
            ) : (
              <p className="muted small">No phone number on file.</p>
            )}
          </li>
        )
      })}
    </ul>
  )
}
