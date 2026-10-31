import { useCallback, useEffect, useState } from 'react'
import {
  listNotifications,
  sendEvent,
  STATUSES,
  type Notification,
  type NotificationStatus,
  type Page,
  type ProviderEvent,
} from './api'
import CreateNotificationForm from './CreateNotificationForm'
import NotificationTable from './NotificationTable'
import Pagination from './Pagination'
import './App.css'

function App() {
  const [status, setStatus] = useState<NotificationStatus | ''>('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page<Notification> | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Bumped to re-fetch the list after a create or an event.
  const [reloadCount, setReloadCount] = useState(0)

  const reload = useCallback(() => setReloadCount((c) => c + 1), [])

  useEffect(() => {
    // Ignore the response if the filter/page changed or another reload started meanwhile.
    let stale = false
    listNotifications(status || undefined, page)
      .then((data) => {
        if (stale) return
        // The page can fall past the end, e.g. when an event moves its last item out of the filter.
        if (data.items.length === 0 && data.page > 0 && data.totalPages > 0) {
          setPage(data.totalPages - 1)
          return
        }
        setResult(data)
        setError(null)
      })
      .catch((err: Error) => {
        if (!stale) setError(err.message)
      })
    return () => {
      stale = true
    }
  }, [status, page, reloadCount])

  // Newest first, so a freshly created notification is on page 0.
  const handleCreated = useCallback(() => {
    setPage(0)
    reload()
  }, [reload])

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

      <CreateNotificationForm onCreated={handleCreated} />

      <section className="list">
        <div className="list-header">
          <h2>Notifications</h2>
          <label>
            Status{' '}
            <select
              value={status}
              onChange={(e) => {
                setStatus(e.target.value as NotificationStatus | '')
                setPage(0)
              }}
            >
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
        {!result ? (
          !error && <p>Loading…</p>
        ) : (
          <>
            <NotificationTable notifications={result.items} onEvent={handleEvent} />
            <Pagination
              page={result.page}
              totalPages={result.totalPages}
              totalElements={result.totalElements}
              onPageChange={setPage}
            />
          </>
        )}
      </section>
    </main>
  )
}

export default App
