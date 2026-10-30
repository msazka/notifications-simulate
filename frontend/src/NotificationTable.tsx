import { useState } from 'react'
import type { Notification, ProviderEvent } from './api'

type Props = {
  notifications: Notification[]
  onEvent: (id: string, event: ProviderEvent) => Promise<void>
}

function NotificationTable({ notifications, onEvent }: Props) {
  const [busyId, setBusyId] = useState<string | null>(null)

  async function handleEvent(id: string, event: ProviderEvent) {
    setBusyId(id)
    try {
      await onEvent(id, event)
    } finally {
      setBusyId(null)
    }
  }

  if (notifications.length === 0) {
    return <p className="empty">No notifications.</p>
  }

  return (
    <table className="notification-table">
      <thead>
        <tr>
          <th>Recipient</th>
          <th>Use case</th>
          <th>Locale</th>
          <th>Status</th>
          <th>Created</th>
          <th aria-label="Actions" />
        </tr>
      </thead>
      <tbody>
        {notifications.map((n) => (
          <tr key={n.id}>
            <td>{n.recipientEmail}</td>
            <td>{n.useCase}</td>
            <td>{n.locale}</td>
            <td>
              <span className={`status status-${n.status.toLowerCase()}`}>{n.status}</span>
            </td>
            <td>{new Date(n.createdAt).toLocaleString()}</td>
            <td className="actions">
              {n.status === 'PENDING' && (
                <>
                  <button disabled={busyId === n.id} onClick={() => handleEvent(n.id, 'sent')}>
                    Simulate sent
                  </button>
                  <button disabled={busyId === n.id} onClick={() => handleEvent(n.id, 'bounce')}>
                    Simulate bounce
                  </button>
                </>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default NotificationTable
