import { useCallback, useEffect, useState } from 'react'
import { api, type ClaimApi } from '../api'
import { dateTime, monthLabel, rand } from '../format'
import type { Claim, OverdueMember } from '../types'
import { useLoader } from './useLoader'

type Part = 'pending' | 'overdue' | 'confirmed'

export default function TreasurerView({ part, apiSet = api }: { part: Part; apiSet?: ClaimApi }) {
  if (part === 'overdue') return <Overdue />
  return <ClaimList mode={part} apiSet={apiSet} />
}

/** Pending claims (confirm / reject) or confirmed payments (reverse a mistake). */
function ClaimList({ mode, apiSet }: { mode: 'pending' | 'confirmed'; apiSet: ClaimApi }) {
  const [items, setItems] = useState<Claim[] | null>(null)
  const [hasMore, setHasMore] = useState(false)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState('')
  const [actionId, setActionId] = useState<number | null>(null)
  const [reason, setReason] = useState('')
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)

  const loadFirstPage = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      if (mode === 'pending') {
        setItems(await apiSet.pending())
        setHasMore(false)
        setPage(0)
      } else {
        const result = await apiSet.confirmed(0)
        setItems(result.items)
        setHasMore(result.hasMore)
        setPage(0)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load data')
    } finally {
      setLoading(false)
    }
  }, [apiSet, mode])

  useEffect(() => {
    loadFirstPage()
  }, [loadFirstPage])

  async function loadMore() {
    setLoadingMore(true)
    setMessage('')
    try {
      const next = page + 1
      const result = await apiSet.confirmed(next)
      setItems((current) => [...(current ?? []), ...result.items])
      setHasMore(result.hasMore)
      setPage(next)
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Could not load more')
    } finally {
      setLoadingMore(false)
    }
  }

  async function run(fn: () => Promise<void>) {
    setBusy(true)
    setMessage('')
    try {
      await fn()
      setActionId(null)
      setReason('')
      await loadFirstPage()
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Action failed')
    } finally {
      setBusy(false)
    }
  }

  function openAction(id: number) {
    setActionId(id)
    setReason('')
    setMessage('')
  }

  if (loading) return <p className="muted">Loading…</p>
  if (error || !items) return <p className="error">{error || 'No data'}</p>
  if (items.length === 0 && !hasMore) {
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
        {items.map((c) => (
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
                <button className="btn primary" disabled={busy} onClick={() => run(() => apiSet.confirm(c.id))}>
                  Confirm
                </button>
                <button className="btn danger" disabled={busy} onClick={() => openAction(c.id)}>
                  Reject
                </button>
              </div>
            )}
            {actionId !== c.id && mode === 'confirmed' && (
              <button className="btn ghost" onClick={() => openAction(c.id)}>
                Reverse (correct a mistake)
              </button>
            )}

            {actionId === c.id && (
              <form
                className="inline-form"
                onSubmit={(e) => {
                  e.preventDefault()
                  run(() => (mode === 'pending' ? apiSet.reject(c.id, reason) : apiSet.reverse(c.id, reason)))
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
      {mode === 'confirmed' && hasMore && (
        <button className="btn ghost" disabled={loadingMore} onClick={loadMore}>
          {loadingMore ? 'Loading…' : 'Load more'}
        </button>
      )}
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
