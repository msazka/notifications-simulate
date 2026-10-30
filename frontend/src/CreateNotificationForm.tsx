import { useState, type FormEvent } from 'react'
import { ApiError, createNotification, LOCALES, type CreateNotificationRequest } from './api'

type Props = {
  onCreated: () => void
}

const EMPTY_FORM: CreateNotificationRequest = {
  producerId: '',
  recipientEmail: '',
  useCase: '',
  locale: 'de',
}

function CreateNotificationForm({ onCreated }: Props) {
  const [form, setForm] = useState<CreateNotificationRequest>(EMPTY_FORM)
  // Kept across failed submits so a retry reuses the same key; renewed only after a 202.
  const [idempotencyKey, setIdempotencyKey] = useState(() => crypto.randomUUID())
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function update<K extends keyof CreateNotificationRequest>(field: K, value: CreateNotificationRequest[K]) {
    setForm((prev) => ({ ...prev, [field]: value }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setSubmitting(true)
    setFieldErrors({})
    setMessage(null)
    try {
      await createNotification(idempotencyKey, form)
      setForm(EMPTY_FORM)
      setIdempotencyKey(crypto.randomUUID())
      onCreated()
    } catch (err) {
      if (err instanceof ApiError) {
        setFieldErrors(err.fieldErrors)
        setMessage(err.message)
      } else {
        setMessage(err instanceof Error ? err.message : 'Request failed')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="create-form" onSubmit={handleSubmit} noValidate>
      <h2>Create notification</h2>

      <label>
        Producer
        <input value={form.producerId} onChange={(e) => update('producerId', e.target.value)} />
        {fieldErrors.producerId && <span className="field-error">{fieldErrors.producerId}</span>}
      </label>

      <label>
        Recipient email
        <input
          type="email"
          value={form.recipientEmail}
          onChange={(e) => update('recipientEmail', e.target.value)}
        />
        {fieldErrors.recipientEmail && <span className="field-error">{fieldErrors.recipientEmail}</span>}
      </label>

      <label>
        Use case
        <input value={form.useCase} onChange={(e) => update('useCase', e.target.value)} />
        {fieldErrors.useCase && <span className="field-error">{fieldErrors.useCase}</span>}
      </label>

      <label>
        Locale
        <select
          value={form.locale}
          onChange={(e) => update('locale', e.target.value as CreateNotificationRequest['locale'])}
        >
          {LOCALES.map((locale) => (
            <option key={locale} value={locale}>
              {locale}
            </option>
          ))}
        </select>
        {fieldErrors.locale && <span className="field-error">{fieldErrors.locale}</span>}
      </label>

      {message && <p className="form-error">{message}</p>}

      <button type="submit" disabled={submitting}>
        {submitting ? 'Creating…' : 'Create'}
      </button>
    </form>
  )
}

export default CreateNotificationForm
