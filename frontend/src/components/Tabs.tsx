import { useState, type ReactNode } from 'react'

interface Tab {
  key: string
  label: string
  content: ReactNode
}

export default function Tabs({ tabs }: { tabs: Tab[] }) {
  const [active, setActive] = useState(tabs[0].key)
  const current = tabs.find((t) => t.key === active) ?? tabs[0]
  return (
    <>
      {tabs.length > 1 && (
        <nav className="tabs" role="tablist">
          {tabs.map((t) => (
            <button
              key={t.key}
              role="tab"
              aria-selected={t.key === current.key}
              className={t.key === current.key ? 'tab active' : 'tab'}
              onClick={() => setActive(t.key)}
            >
              {t.label}
            </button>
          ))}
        </nav>
      )}
      {/* key forces a fresh load when switching tabs */}
      <section key={current.key}>{current.content}</section>
    </>
  )
}
