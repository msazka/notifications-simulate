# Coding Challenge 1: Notification Status Board

**Time:** 60 minutes. **Tools:** any AI tool you like. Talk out loud as if three interviewers are listening.

## Context
Internal services send notification requests to our platform. The operations team needs a small tool to
create notifications, see their status, and simulate delivery feedback from the email provider.

## Backend (Spring Boot)

### 1. Create a notification
`POST /api/notifications`

- Header `Idempotency-Key` is required. Missing key returns **400**.
- Body:
  ```json
  { "producerId": "billing", "recipientEmail": "max@example.com", "useCase": "dunning", "locale": "de" }
  ```
- Validation (return **400** with readable error messages):
    - `producerId`, `useCase`: not blank
    - `recipientEmail`: valid email
    - `locale`: one of `de`, `en`, `fr`, `it`, `nl`
- A new request returns **202** with `{ "id": "...", "status": "PENDING" }`.
- Same `producerId` + same key + same body returns **202** with the **same id**. Nothing new is created.
- Same `producerId` + same key + different body returns **409**.

### 2. List notifications
`GET /api/notifications?status=PENDING`

- Newest first. The `status` filter is optional.

### 3. Simulate a provider event
`POST /api/notifications/{id}/events` with body `{ "event": "sent" }` or `{ "event": "bounce" }`

| Current status | Event | New status |
|---|---|---|
| `PENDING` | `sent` | `DELIVERED` |
| `PENDING` | `bounce` | `BOUNCED` |
| `DELIVERED` or `BOUNCED` | any | unchanged (return 200, no error) |

- Unknown id returns **404**. Unknown event returns **400**.

## Frontend (React + TypeScript)

4. A table of notifications: recipient, use case, locale, status, created time.
5. A status filter above the table.
6. A form to create a notification. The client generates the idempotency key. API validation errors are shown next to the form.
7. On each `PENDING` row, two buttons: **Simulate sent** and **Simulate bounce**. The table refreshes after a click.

## Tests (required)
- Same key and same body returns the same id.
- Same key and different body returns 409.
- An event on a final status changes nothing.

## Stretch goals (only if time is left)
- Two identical requests arriving at the same time still create only one notification.
- Pagination on the list.
- The table refreshes automatically every few seconds.

## Suggested timeline
| Minutes | Goal |
|---|---|
| 0–5 | Read the task, ask Claude for a plan, review and adjust it before any code |
| 5–25 | Backend and tests, all green. Commit. |
| 25–45 | Frontend. Commit. |
| 45–55 | Run everything end to end, fix what's broken. Commit. |
| 55–60 | Explain your decisions and what you would do next |