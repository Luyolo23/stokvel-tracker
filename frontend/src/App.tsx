import { useEffect, useState } from 'react'
import { api, HttpError, logout, setUnauthorizedHandler } from './api'
import type { Me } from './types'
import Login from './components/Login'
import Tabs from './components/Tabs'
import MemberView from './components/MemberView'
import TreasurerView from './components/TreasurerView'
import ChairView from './components/ChairView'

export default function App() {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.me()
      .then(setMe)
      .catch((e) => {
        if (!(e instanceof HttpError && e.status === 401)) console.error(e)
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => setMe(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  async function handleLogout() {
    await logout()
    setMe(null)
  }

  if (loading) return <p className="center muted">Loading…</p>
  if (!me) return <Login onSignedIn={setMe} />

  const tabs =
    me.role === 'CHAIR'
      ? [
          { key: 'summary', label: 'Summary', content: <ChairView part="summary" /> },
          { key: 'audit', label: 'Audit log', content: <ChairView part="audit" /> },
          { key: 'mine', label: 'My payments', content: <MemberView /> },
        ]
      : me.role === 'TREASURER'
        ? [
            { key: 'confirm', label: 'To confirm', content: <TreasurerView part="pending" /> },
            { key: 'overdue', label: 'Overdue', content: <TreasurerView part="overdue" /> },
            { key: 'done', label: 'Confirmed', content: <TreasurerView part="confirmed" /> },
            { key: 'mine', label: 'My payments', content: <MemberView /> },
          ]
        : [{ key: 'mine', label: 'My payments', content: <MemberView /> }]

  return (
    <div className="app">
      <header className="topbar">
        <div>
          <strong>{me.groupName}</strong>
          <div className="muted small">
            {me.displayName} · {me.role.toLowerCase()}
          </div>
        </div>
        <button className="btn ghost" onClick={handleLogout}>
          Sign out
        </button>
      </header>
      <main>
        <Tabs tabs={tabs} />
      </main>
    </div>
  )
}
