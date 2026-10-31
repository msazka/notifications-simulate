export const STATUSES = ['PENDING', 'DELIVERED', 'BOUNCED'] as const
export type NotificationStatus = (typeof STATUSES)[number]

export const LOCALES = ['de', 'en', 'fr', 'it', 'nl'] as const
export type Locale = (typeof LOCALES)[number]

export type ProviderEvent = 'sent' | 'bounce'

export type Notification = {
  id: string
  producerId: string
  recipientEmail: string
  useCase: string
  locale: string
  status: NotificationStatus
  createdAt: string
}

export type CreateNotificationRequest = {
  producerId: string
  recipientEmail: string
  useCase: string
  locale: Locale
}

export type CreateNotificationResponse = {
  id: string
  status: NotificationStatus
}

type ErrorBody = {
  message?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string>) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  const body: unknown = await res.json().catch(() => null)
  if (!res.ok) {
    const error = (body ?? {}) as ErrorBody
    throw new ApiError(res.status, error.message ?? `HTTP ${res.status}`, error.errors ?? {})
  }
  return body as T
}

export const PAGE_SIZE = 20

export type Page<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export function listNotifications(
  status: NotificationStatus | undefined,
  page: number,
  size: number = PAGE_SIZE,
): Promise<Page<Notification>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  return request<Page<Notification>>(`/api/notifications?${params}`)
}

export function createNotification(
  idempotencyKey: string,
  body: CreateNotificationRequest,
): Promise<CreateNotificationResponse> {
  return request<CreateNotificationResponse>('/api/notifications', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(body),
  })
}

export function sendEvent(id: string, event: ProviderEvent): Promise<Notification> {
  return request<Notification>(`/api/notifications/${id}/events`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ event }),
  })
}
