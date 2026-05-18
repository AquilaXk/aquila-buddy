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

## Runtime Configuration

기본 운영 AI provider는 `vertex-ai`입니다.

- `AI_PROVIDER=vertex-ai`: Google Vertex AI Gemini completion/embedding provider를 사용합니다.
- `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`: 사용자 profile embedding과 issue embedding의 pgvector similarity ranking을 사용합니다.
- `LANGFUSE_ENABLED=true`: AI 호출을 Langfuse generation event로 함께 전송합니다. 기본값은 `false`입니다.

Vertex AI smoke에 필요한 기본 env:

```bash
GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project
VERTEX_AI_LOCATION=us-central1
VERTEX_AI_MODEL=gemini-2.5-flash
VERTEX_AI_EMBEDDING_MODEL=gemini-embedding-001
VERTEX_AI_EMBEDDING_DIMENSIONS=1536
VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json
GOOGLE_APPLICATION_CREDENTIALS=./vertex-key.json
```

`vertex-key.json` 같은 credential 파일은 로컬에만 두고 git에 추가하지 않습니다.
`.gitignore`는 `vertex-key.json`, `**/vertex-key.json`, credential/service-account JSON 패턴을 제외합니다.

Langfuse tracing은 선택 기능이며 secret key는 환경 변수로만 주입합니다.

```bash
LANGFUSE_ENABLED=false
LANGFUSE_BASE_URL=https://cloud.langfuse.com
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
LANGFUSE_ENVIRONMENT=local
```

GitHub 연동 smoke에는 repository/issue 조회가 가능한 token이 필요합니다.
OAuth 로그인을 함께 확인할 때는 `read:user`, `user:email` scope의 GitHub OAuth app 설정도 필요합니다.

## Runtime Smoke

PostgreSQL/pgvector와 실제 AI provider를 함께 확인하는 최소 순서:

```bash
docker compose up -d
bash tools/smoke/validate-ai-runtime-env.sh
SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun
```

필수 env가 빠진 상태에서는 validation script가 실패해야 합니다.

```bash
bash tools/smoke/validate-ai-runtime-env.sh
```

Vertex AI + pgvector smoke env 예시:

```bash
AI_PROVIDER=vertex-ai \
GOOGLE_CLOUD_PROJECT_ID=grepp-ai-project \
VERTEX_AI_LOCATION=us-central1 \
VERTEX_AI_MODEL=gemini-2.5-flash \
VERTEX_AI_EMBEDDING_MODEL=gemini-embedding-001 \
VERTEX_AI_EMBEDDING_DIMENSIONS=1536 \
VERTEX_AI_CREDENTIALS_PATH=./vertex-key.json \
GOOGLE_APPLICATION_CREDENTIALS=./vertex-key.json \
RECOMMENDATION_SIMILARITY_PROVIDER=pgvector \
RECOMMENDATION_SIMILARITY_LIMIT=20 \
DB_URL=jdbc:postgresql://localhost:5432/aquila_buddy \
DB_USER=aquila \
DB_PASSWORD=aquila \
GITHUB_TOKEN=github_pat_or_fine_grained_token \
bash tools/smoke/validate-ai-runtime-env.sh
```

수동 smoke 흐름:

1. OAuth 로그인 또는 `DEFAULT_GITHUB_ID` fallback 사용자로 profile을 준비합니다.
2. profile vector refresh를 실행해 `profile_embedding` 저장을 확인합니다.
3. issue crawl/search를 실행해 `issue_embedding` 저장을 확인합니다.
4. `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`로 추천 순위가 embedding similarity를 반영하는지 확인합니다.
5. issue guide, PR draft, translation을 실행해 AI 호출과 `ai_call_observations` 저장을 확인합니다.
6. `LANGFUSE_ENABLED=true` 환경에서는 같은 호출이 Langfuse generation event로 적재되는지 확인합니다.

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
