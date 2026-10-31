---
name: review-diff
description: Review the uncommitted changes (or a given branch/commit range) in this repo against the project conventions in CLAUDE.md, run the relevant builds/tests, and report findings. Use when asked to "review my diff", "review my changes", "check this before I commit", or /review-diff.
---

# Review diff

Review local changes in this repository for correctness and for the conventions in
`CLAUDE.md`. This is a **read-only review**: do not edit files, stage, or commit unless the
user explicitly asks for fixes afterwards.
Run this review in a subagent with a fresh context, so the review is independent of the
conversation that wrote the code. The subagent returns only the report.

## 1. Collect the diff

- No argument: review everything not yet committed.
  ```bash
  git status --short
  git diff HEAD            # staged + unstaged
  ```
  Also read any **untracked** files listed by `git status` in full — `git diff` does not show them.
- Argument given (branch, commit, or range such as `main..feature`): `git diff <arg>`.
- If there is nothing to review, say so and stop.

Read enough surrounding code (the whole changed method/class, its callers, related tests)
to judge the change. Don't review from the diff hunks alone.

## 2. Check correctness

For each change, look for:

- Logic errors, wrong conditions, off-by-one, null/Optional handling, missed edge cases.
- **Transactions & JPA**: `@Transactional` where needed; bulk `@Modifying` queries and
  persistence-context staleness (`clearAutomatically`/`flushAutomatically`); order of
  update vs. read; N+1 queries; lazy loading outside a transaction.
- **Concurrency**: races on status transitions (conditional updates should stay conditional).
- **Validation & errors**: request DTOs validated (`@Valid`, `jakarta.validation` constraints);
  correct HTTP status codes; not-found and conflict cases handled. 
- **Tests that prove nothing**: tests that would still pass if the code were wrong,
    e.g. a mocked repository returning the expected final state, so the order of
    write and read is never checked.
- **Scope creep**: changes the task did not ask for (new validation rules, new
  dependencies, refactors). List them so the author can decide to keep or drop them.
- **Frontend**: hook dependency arrays, loading/error states, unhandled promise rejections,
  types that hide `any`.
- Security: injection, unvalidated input reaching queries, secrets in code or config.

## 3. Check project conventions (from CLAUDE.md)

Backend (`backend/`, Spring Boot 4, Java 21):
- Package by feature: `com.azka.notifications.<feature>` holds controller, service,
  repository, entities, DTOs.
- Thin controllers: only routing, binding, validation annotations, status codes. Any
  business logic in a controller is a finding.
- Business rules live in `@Service` classes; controllers → services → repositories.
- DTOs are Java `record`s; JPA entities never returned from controllers.
- **Every new or changed business rule has at least one unit test.** Controller changes are
  covered with `@WebMvcTest` + MockMvc. Missing tests are a finding.
- All REST endpoints under `/api`.
- Imports use `jakarta.*` (not `javax.*`) — Spring Boot 4 is Jakarta EE.
- No H2-specific APIs or SQL in main code (H2 is test/`h2` profile only).
- Schema via `ddl-auto: update`; flag entity changes that `update` can't apply safely
  (renames, type changes, new NOT NULL columns on existing data).

Frontend (`frontend/`, React + TypeScript, Vite):
- Function components with hooks, TypeScript throughout.
- Plain CSS only — no UI component libraries or CSS frameworks in `package.json` or imports.
- Backend calls use relative `/api/...` URLs (no hard-coded `http://localhost:8080`).

## 4. Verify

Run only what the diff touches:

| Changed | Command |
| --- | --- |
| `backend/**` | `cd backend && ./gradlew build` (compiles + all tests on H2, no Docker) |
| `frontend/**` | `cd frontend && npm run build && npm run lint` |

If a build or test fails, include the relevant error output. Report results faithfully —
do not claim something passes without having run it.

## 5. Report

Reply in this shape, most severe first:

```
## Review: <n> files changed

**Verdict:** ready to commit | needs changes

### Findings
1. **[bug|convention|missing-test|risk|nit]** `path/File.java:42` — what is wrong, and the
   concrete scenario where it breaks. Suggested fix in one or two lines.
...

### Checks
- Backend build/tests: passed | failed (summary) | not run (no backend changes)
- Frontend build/lint: passed | failed (summary) | not run (no frontend changes)
```

- Use `file_path:line` references.
- Only report issues you have verified by reading the code; mark anything uncertain as
  **risk** and say what you'd need to confirm it.
- Skip pure style nits unless they break a convention above.
- If there are no findings, say so plainly.

After the report, offer to apply fixes — but don't apply them without the user's go-ahead.
Commits for backend and frontend changes should stay separate.
