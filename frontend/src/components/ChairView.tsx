import { api } from '../api'
import { dateTime, monthLabel, rand } from '../format'
import { useLoader } from './useLoader'

export default function ChairView({ part }: { part: 'summary' | 'audit' }) {
  return part === 'summary' ? <Summary /> : <Audit />
}

function Summary() {
  const { data, error, loading } = useLoader(api.summary)
  if (loading) return <p className="muted">Loading…</p>
  if (error || !data) return <p className="error">{error || 'No data'}</p>

  return (
    <>
      <h2>{monthLabel(data.month)}</h2>
      <div className="stats">
        <div className="stat"><span className="muted small">Collected</span>
          <strong>{rand(data.collectedThisMonthCents)}</strong>
          <span className="muted small">of {rand(data.expectedThisMonthCents)}</span></div>
        <div className="stat"><span className="muted small">Owed (all months)</span>
          <strong>{rand(data.owedTotalCents)}</strong></div>
        <div className="stat"><span className="muted small">Members behind</span>
          <strong>{data.membersBehind}</strong>
          <span className="muted small">{data.pendingCount} awaiting confirmation</span></div>
      </div>

      <h3>Members</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Member</th><th>Paid</th><th>Owed</th><th>Late</th></tr>
          </thead>
          <tbody>
            {data.members.map((m) => (
              <tr key={m.memberName}>
                <td>{m.memberName}</td>
                <td>{rand(m.confirmedCents)}</td>
                <td>{rand(m.owedCents)}</td>
                <td>{m.overdueCount > 0 ? <span className="badge overdue">{m.overdueCount}</span> : '–'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

function Audit() {
  const { data, error, loading } = useLoader(api.audit)
  if (loading) return <p className="muted">Loading…</p>
  if (error || !data) return <p className="error">{error || 'No data'}</p>
  if (data.length === 0) return <p className="muted center">No activity yet.</p>

  return (
    <ul className="list">
      {data.map((a) => (
        <li key={a.id} className="card">
          <div className="row">
            <strong>{a.action}</strong>
            <span className="muted small">{dateTime(a.createdAt)}</span>
          </div>
          <p className="small">{a.detail}</p>
          <p className="muted small">by {a.actor}</p>
        </li>
      ))}
    </ul>
  )
}
