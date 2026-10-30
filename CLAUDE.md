# CLAUDE.md

Guidance for AI assistants and contributors working in this repository.

## Structure

```
.
├── docker-compose.yml          # PostgreSQL 16 for local development
├── backend/                    # Spring Boot 4, Java 21, Gradle
│   ├── build.gradle
│   └── src/
│       ├── main/java/com/azka/notifications/
│       │   ├── NotificationsApplication.java
│       │   └── <feature>/      # one package per feature, e.g. ping/
│       ├── main/resources/application.yml   # PostgreSQL datasource, port 8080
│       ├── main/resources/application-h2.yml  # "h2" profile: in-memory H2 + console
│       ├── test/java/com/azka/notifications/
│       └── test/resources/application.yml   # H2 in-memory, ddl-auto: create-drop
└── frontend/                   # React + TypeScript, Vite, plain CSS
    ├── vite.config.ts          # proxies /api -> http://localhost:8080
    └── src/
```

## Commands

| Task | Command |
| --- | --- |
| Start database | `docker compose up -d` |
| Run backend (PostgreSQL) | `cd backend && ./gradlew bootRun` |
| Run backend (H2, no Docker) | `cd backend && ./gradlew bootRun --args='--spring.profiles.active=h2'` |
| Test backend | `cd backend && ./gradlew test` |
| Install frontend deps | `cd frontend && npm install` |
| Run frontend | `cd frontend && npm run dev` (http://localhost:5173) |
| Build frontend | `cd frontend && npm run build` |
| Lint frontend | `cd frontend && npm run lint` |

Backend tests run against in-memory H2 and need no Docker. Both the PostgreSQL and H2
drivers are `runtimeOnly`; H2 is used only by tests and the `h2` profile. Main code must
not depend on H2-specific APIs or SQL.

## Conventions

### Backend

- **Package by feature**: `com.azka.notifications.<feature>` holds that feature's controller,
  service, repository, entities, and DTOs.
- **Thin controllers**: controllers only map HTTP to service calls (routing, request binding,
  validation annotations, status codes). No business logic in controllers.
- **Logic in services**: business rules live in `@Service` classes. Controllers call services;
  services call repositories.
- **Java records for DTOs**: request and response bodies are `record`s. Do not expose JPA
  entities directly from controllers.
- **A test for every business rule**: each rule in a service has at least one unit test.
  Use `@WebMvcTest` + MockMvc for controller tests.
- All REST endpoints live under `/api`.
- The schema is managed by `spring.jpa.hibernate.ddl-auto: update` for now (no migrations yet).

### Frontend

- React function components with hooks, TypeScript throughout.
- Plain CSS only. No UI component libraries or CSS frameworks.
- Call the backend through relative `/api/...` URLs so the Vite proxy handles routing.
