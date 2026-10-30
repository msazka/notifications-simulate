import { useEffect, useState } from 'react'
import './App.css'

type PingResponse = {
  status: string
}

type PingState =
  | { kind: 'loading' }
  | { kind: 'ok'; data: PingResponse }
  | { kind: 'error'; message: string }

function App() {
  const [ping, setPing] = useState<PingState>({ kind: 'loading' })

  useEffect(() => {
    fetch('/api/ping')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<PingResponse>
      })
      .then((data) => setPing({ kind: 'ok', data }))
      .catch((err: Error) => setPing({ kind: 'error', message: err.message }))
  }, [])

  return (
    <main className="app">
      <h1>Notifications</h1>
      <section className="ping">
        <h2>Backend status</h2>
        {ping.kind === 'loading' && <p>Checking…</p>}
        {ping.kind === 'ok' && (
          <p className="ping-ok">
            /api/ping → <code>{JSON.stringify(ping.data)}</code>
          </p>
        )}
        {ping.kind === 'error' && (
          <p className="ping-error">Backend unreachable: {ping.message}</p>
        )}
      </section>
    </main>
  )
}

export default App
