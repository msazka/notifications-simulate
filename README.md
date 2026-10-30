# Notifications

Spring Boot backend (Java 21, Gradle) + React/TypeScript frontend (Vite), backed by PostgreSQL 16.

## Prerequisites

- Java 21
- Node.js 20+ and npm
- Docker with Docker Compose

## Run

### 1. Start PostgreSQL

```bash
docker compose up -d
```

Database `notifications`, user `app`, password `app`, on `localhost:5432`.

### 2. Run the backend

With PostgreSQL (needs step 1):

```bash
cd backend
./gradlew bootRun
```

Or with the `h2` profile (in-memory H2, no Docker needed; data is lost on restart):

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=h2'
```

The H2 console is at http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:notifications`, user `sa`, empty password).

The API runs on http://localhost:8080. Check it with `curl http://localhost:8080/api/ping`.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to the backend on port 8080.

## Test

```bash
# Backend (uses in-memory H2, no Docker needed)
cd backend
./gradlew test

# Frontend (type-check + production build)
cd frontend
npm run build
```

## Stop

```bash
docker compose down        # keep data
docker compose down -v     # also delete the database volume
```
