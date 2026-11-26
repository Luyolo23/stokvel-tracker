import { useState } from 'react'
import { api } from '../api'
import { monthLabel, rand } from '../format'
import StatusBadge from './StatusBadge'
import { useLoader } from './useLoader'

export default function MemberView() {
  const { data, error, loading, reload } = useLoader(api.overview)
  const [claimingId, setClaimingId] = useState<number | null>(null)
  const [reference, setReference] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submitClaim(id: number) {
    setBusy(true)
    setFormError('')
    try {
      await api.claim(id, reference)
      setClaimingId(null)
      setReference('')
      await reload()
    } catch (e) {
      setFormError(e instanceof Error ? e.message : 'Could not send')
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <p className="muted">Loading…</p>
  if (error || !data) return <p className="error">{error || 'No data'}</p>

  return (
    <>
      <div className="stats">
        <div className="stat"><span className="muted small">Paid</span><strong>{rand(data.paidCents)}</strong></div>
        <div className="stat"><span className="muted small">Awaiting</span><strong>{rand(data.pendingCents)}</strong></div>
        <div className="stat"><span className="muted small">Still owed</span><strong>{rand(data.owedCents)}</strong></div>
      </div>

      <ul className="list">
        {data.contributions.map((c) => {
          const canClaim = c.status === 'DUE' || c.status === 'REJECTED'
          return (
            <li key={c.id} className="card">
              <div className="row">
                <div>
                  <strong>{monthLabel(c.month)}</strong>
                  <div className="muted small">
                    {rand(c.amountDueCents)} · due {c.dueDate}
                  </div>
                </div>
                <StatusBadge c={c} />
              </div>
              {c.status === 'REJECTED' && c.rejectReason && (
                <p className="error small">Rejected: {c.rejectReason}</p>
              )}
              {c.reference && c.status !== 'DUE' && (
                <p className="muted small">Reference: {c.reference}</p>
              )}
              {canClaim && claimingId !== c.id && (
                <button className="btn primary" onClick={() => { setClaimingId(c.id); setFormError('') }}>
                  I've paid
                </button>
              )}
              {claimingId === c.id && (
                <form
                  onSubmit={(e) => { e.preventDefault(); submitClaim(c.id) }}
                  className="inline-form"
                >
                  <label>
                    Payment reference
                    <input
                      value={reference}
                      onChange={(e) => setReference(e.target.value)}
                      placeholder="e.g. your EFT reference or 'cash'"
                      maxLength={100}
                      autoFocus
                      required
                    />
                  </label>
                  {formError && <p className="error small" role="alert">{formError}</p>}
                  <div className="row gap">
                    <button className="btn primary" disabled={busy}>Send to treasurer</button>
                    <button type="button" className="btn ghost" onClick={() => setClaimingId(null)}>Cancel</button>
                  </div>
                </form>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}
