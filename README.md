# Aquila Buddy

Java 21 + Spring Boot and Next.js scaffold for an OMOS-style open source contribution assistant.

## Runtime Apps

- `back`: Java 21, Spring Boot 3.5, Gradle, Spring MVC, Security, OAuth2 Client, JPA, PostgreSQL, pgvector
- `front`: Next.js 16, React 19, TypeScript, Tailwind CSS

## Local Start

Backend defaults to an embedded H2 database for quick local smoke checks.

```bash
./back/gradlew -p back bootRun
```

PostgreSQL/pgvector can be used with the `dev` profile:

```bash
docker compose up -d
SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun
```

```bash
cd front
npm install
npm run dev
```

## Ports

- Backend: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Frontend: `http://localhost:3000`
- PostgreSQL: `localhost:5432`

## Verification

```bash
./back/gradlew -p back check
npm --prefix front run typecheck
npm --prefix front run build
```

## Agent Docs

Machine-facing agent settings are local-only and ignored by git:

- `AGENTS.md`
- `docs/AGENT-CONTEXT.md`
- `docs/agent/*.md`
- `.codex/tasks/*.local`
