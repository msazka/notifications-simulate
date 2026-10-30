import { useCallback, useEffect, useState } from 'react'
import {
  listNotifications,
  sendEvent,
  STATUSES,
  type Notification,
  type NotificationStatus,
  type ProviderEvent,
} from './api'
import CreateNotificationForm from './CreateNotificationForm'
import NotificationTable from './NotificationTable'
import './App.css'

function App() {
  const [status, setStatus] = useState<NotificationStatus | ''>('')
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // Bumped to re-fetch the list after a create or an event.
  const [reloadCount, setReloadCount] = useState(0)

  const reload = useCallback(() => setReloadCount((c) => c + 1), [])

  useEffect(() => {
    // Ignore the response if the filter changed or another reload started meanwhile.
    let stale = false
    listNotifications(status || undefined)
      .then((data) => {
        if (stale) return
        setNotifications(data)
        setError(null)
      })
      .catch((err: Error) => {
        if (!stale) setError(err.message)
      })
      .finally(() => {
        if (!stale) setLoaded(true)
      })
    return () => {
      stale = true
    }
  }, [status, reloadCount])

  async function handleEvent(id: string, event: ProviderEvent) {
    try {
      await sendEvent(id, event)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to send event')
    }
    reload()
  }

  return (
    <main className="app">
      <h1>Notifications</h1>

      <CreateNotificationForm onCreated={reload} />

      <section className="list">
        <div className="list-header">
          <h2>Notifications</h2>
          <label>
            Status{' '}
            <select value={status} onChange={(e) => setStatus(e.target.value as NotificationStatus | '')}>
              <option value="">All</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
        </div>

        {error && <p className="banner-error">{error}</p>}
        {!loaded ? (
          <p>Loading…</p>
        ) : (
          <NotificationTable notifications={notifications} onEvent={handleEvent} />
        )}
      </section>
    </main>
  )
}

export default App
