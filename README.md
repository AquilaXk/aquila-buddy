<!-- PROJECT LOGO -->
<p align="center">
  <a href="https://github.com/AquilaXk/aquila-buddy">
    <img src="front/public/brand-mascot.png" alt="Aquila Buddy" width="160">
  </a>
</p>

<h3 align="center">Aquila Buddy</h3>

<p align="center">
  GitHub issue 추천, 코드 맥락 분석, PR 초안 작성을 한 흐름으로 묶는 오픈소스 기여 도우미입니다.
  <br />
  <a href="https://buddy.aquilaxk.site"><strong>서비스 바로가기 »</strong></a>
  <br />
  <br />
  <a href="https://github.com/AquilaXk/aquila-buddy/issues">Issues</a>
  ·
  <a href="https://github.com/AquilaXk/aquila-buddy/pulls">Pull Requests</a>
  ·
  <a href="tools/smoke/AI_RUNTIME_SMOKE.md">AI Smoke</a>
  ·
  <a href="deploy/oci/README.md">Deploy</a>
</p>

<p align="center">
  <a href="https://github.com/AquilaXk/aquila-buddy/actions/workflows/ci.yml">
    <img src="https://github.com/AquilaXk/aquila-buddy/actions/workflows/ci.yml/badge.svg" alt="CI">
  </a>
  <a href="https://github.com/AquilaXk/aquila-buddy/actions/workflows/provider-e2e-smoke.yml">
    <img src="https://github.com/AquilaXk/aquila-buddy/actions/workflows/provider-e2e-smoke.yml/badge.svg" alt="Provider E2E Smoke">
  </a>
  <a href="https://github.com/AquilaXk/aquila-buddy/actions/workflows/extension-github-live-smoke.yml">
    <img src="https://github.com/AquilaXk/aquila-buddy/actions/workflows/extension-github-live-smoke.yml/badge.svg" alt="Extension GitHub Live Smoke">
  </a>
  <img src="https://img.shields.io/badge/Java-21-007396" alt="Java 21">
  <img src="https://img.shields.io/badge/Next.js-16-black" alt="Next.js 16">
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791" alt="PostgreSQL pgvector">
</p>

<!-- ABOUT THE PROJECT -->

## About the Project

![Aquila Buddy mascot](front/public/brand-mascot.png)

# 오픈소스 기여 준비를 위한 AI workflow infrastructure

Aquila Buddy는 사용자를 대신해 upstream PR을 자동으로 만들거나 병합하는 서비스가 아닙니다. GitHub OAuth로 로그인한 사용자의 프로필을 준비하고, Good First Issue 후보를 수집한 뒤, profile vector와 issue vector를 기준으로 추천 순위를 계산합니다.

추천된 이슈에서는 repository code context를 모아 수정 가이드, 의사 코드, side effect를 정리합니다. 이후 사용자는 issue context, diff context, repository guide context를 반영한 PR 초안과 영문 번역을 생성해 제출 전 문안을 다듬을 수 있습니다.

## Project Status

- 운영 도메인: `https://buddy.aquilaxk.site`
- 기본 backend runtime: Spring Boot 3.5 / Java 21
- 기본 frontend runtime: Next.js 16 / Node.js 22 container
- 운영 database: PostgreSQL with pgvector
- 기본 AI provider: `vertex-ai`
- 추천 ranking: `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`
- live provider, Langfuse, GitHub DOM smoke는 secret과 외부 인증 상태가 필요한 opt-in workflow입니다.

기본 운영 AI provider는 `vertex-ai`입니다.

### Built With

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Java 21](https://openjdk.org/projects/jdk/21/)
- [Next.js](https://nextjs.org)
- [React](https://react.dev)
- [Tailwind CSS](https://tailwindcss.com)
- [PostgreSQL](https://www.postgresql.org)
- [pgvector](https://github.com/pgvector/pgvector)
- [GitHub Actions](https://github.com/features/actions)

## Contact

상용 지원 창구는 별도로 운영하지 않습니다. 기능 제안, 결함, 운영 smoke 요청은 [GitHub Issues](https://github.com/AquilaXk/aquila-buddy/issues)에 남겨 주세요.

## Stay Up-to-Date

주요 변경은 `main` 대상 PR, GitHub Actions 결과, release readiness record로 추적합니다. 운영 전환이나 provider smoke 상태를 확인할 때는 다음 문서를 먼저 확인합니다.

- [AI 운영 smoke runbook](tools/smoke/AI_RUNTIME_SMOKE.md)
- [release readiness records](tools/smoke/release-readiness/README.md)
- [동등성 완료 매트릭스](tools/smoke/release-readiness/PARITY_COMPLETION_MATRIX.md)

<!-- GETTING STARTED -->

## Getting Started

로컬에서 빠르게 확인할 때는 H2 기반 backend와 Next.js frontend를 먼저 실행합니다. pgvector 추천, OAuth, AI provider, Langfuse smoke는 필요한 secret과 외부 계정을 준비한 뒤 opt-in으로 켭니다.

### Prerequisites

- Java 21
- Node.js LTS
- npm
- Docker Compose
- Chrome 또는 Chromium, extension live smoke 실행 시
- GitHub OAuth app, 실제 로그인을 확인할 때

## Development

### Setup

1. 저장소를 clone합니다.

   ```bash
   git clone https://github.com/AquilaXk/aquila-buddy.git
   cd aquila-buddy
   ```

2. backend와 frontend 예시 환경 변수를 준비합니다.

   ```bash
   cp .env.example .env.local
   cp back/.env.example back/.env.local
   cp front/.env.local.example front/.env.local
   ```

3. frontend package를 설치합니다.

   ```bash
   npm --prefix front install
   ```

4. backend를 H2 local smoke 모드로 실행합니다.

   ```bash
   ./back/gradlew -p back bootRun
   ```

5. frontend를 실행합니다.

   ```bash
   NEXT_PUBLIC_API_URL=http://localhost:8080 npm --prefix front run dev
   ```

6. 브라우저에서 `http://localhost:3000`을 엽니다.

#### Quick start with Docker Compose

PostgreSQL/pgvector 개발 인프라가 필요하면 compose의 `postgres` service를 먼저 띄웁니다.

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun
```

- Backend: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Frontend: `http://localhost:3000`
- PostgreSQL/pgvector: `localhost:5432`
- Database: `aquila_buddy`
- User/password: `aquila` / `aquila`

#### Development tip

frontend 포트를 바꿀 때는 `PORT`를 함께 지정합니다.

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080 PORT=3001 npm --prefix front run dev
```

backend 연결 오류가 보이면 다음 순서로 확인합니다.

1. `curl http://localhost:8080/actuator/health`가 성공하는지 확인합니다.
2. frontend shell에 `NEXT_PUBLIC_API_URL=http://localhost:8080`이 들어갔는지 확인합니다.
3. `SPRING_PROFILES_ACTIVE=dev`를 사용할 때는 `docker compose up -d postgres`가 먼저 실행됐는지 확인합니다.

#### Manual setup

실제 GitHub OAuth와 AI provider를 확인하려면 `.env.local` 또는 private 운영 env에 아래 값을 채웁니다. credential 파일과 secret은 git에 추가하지 않습니다.

```bash
GITHUB_CLIENT_ID=
GITHUB_CLIENT_SECRET=
GITHUB_TOKEN=
JWT_SECRET=change-me-to-a-random-32-byte-secret
OAUTH2_REDIRECT_URI=http://localhost:3000/oauth/callback
AI_PROVIDER=vertex-ai
RECOMMENDATION_SIMILARITY_PROVIDER=score
```

OpenAI-compatible provider로 smoke를 실행할 때는 provider, GitHub token 권한, pgvector 설정을 함께 확인합니다.

```bash
AI_PROVIDER=openai-compatible \
AI_API_BASE_URL=https://api.openai.com/v1 \
AI_API_KEY=openai_or_compatible_api_key \
AI_CHAT_MODEL=gpt-4o-mini \
AI_TEMPERATURE=0.2 \
AI_EMBEDDING_MODEL=text-embedding-3-small \
AI_EMBEDDING_DIMENSIONS=1536 \
RECOMMENDATION_SIMILARITY_PROVIDER=pgvector \
RECOMMENDATION_SIMILARITY_LIMIT=20 \
GITHUB_TOKEN=github_pat_or_fine_grained_token \
GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read \
bash tools/smoke/validate-ai-runtime-env.sh
```

#### Setting up your first user

로컬 로그인은 GitHub OAuth app의 callback과 frontend callback URL이 맞아야 합니다.

- local callback: `http://localhost:3000/oauth/callback`
- backend OAuth entrypoint: `http://localhost:8080/oauth2/authorization/github`
- frontend callback route: `/oauth/callback`

로그인 후 profile page에서 이름, email, 관심 topic을 저장하면 profile vector refresh 경로를 확인할 수 있습니다.

### E2E Testing

기본 검증은 secret 없이 실행할 수 있는 unit, contract, fixture smoke로 구성됩니다.

```bash
./back/gradlew -p back check
npm --prefix front run typecheck
npm --prefix front run build
npm --prefix front run test:e2e
npm --prefix front run smoke:integration
npm --prefix extension run validate
npm --prefix extension run test:e2e:contracts
```

pgvector 통합 검증은 격리된 PostgreSQL/pgvector 컨테이너로 실행합니다.

```bash
bash tools/test/run-pgvector-integration.sh --compose --dry-run
PGVECTOR_TEST_DB_PORT=54329 bash tools/test/run-pgvector-integration.sh --compose
```

frontend smoke는 기본 CI에서 fixture backend를 띄워 보호 라우트, success envelope, failure envelope를 브라우저로 확인합니다. CI release gate처럼 `npm run build` 산출물을 검증할 때는 `next start` 모드를 사용합니다.

```bash
NEXT_PUBLIC_API_URL=http://127.0.0.1:18080 \
FRONTEND_INTEGRATION_SERVER_MODE=start \
FRONTEND_INTEGRATION_FIXTURE_BACKEND_PORT=18080 \
FRONTEND_INTEGRATION_ROUTE_TIMEOUT_MS=20000 \
npm --prefix front run smoke:integration
```

실제 backend를 대상으로 수동 smoke를 실행할 때는 live backend URL을 지정합니다. 이 모드는 local fixture backend를 사용하지 않으므로 backend, OAuth/JWT, 데이터 준비 상태를 먼저 확인해야 합니다.

```bash
FRONTEND_INTEGRATION_USE_LIVE_BACKEND=true \
NEXT_PUBLIC_API_URL=http://localhost:8080 \
npm --prefix front run smoke:integration
```

#### Resolving issues

- `403` 또는 `401`: OAuth/JWT cookie, `JWT_SECRET`, GitHub OAuth app callback URL을 확인합니다.
- 추천 결과가 비어 있음: `GITHUB_ISSUE_SEARCH_ENABLED`, `GITHUB_TOKEN_PERMISSIONS`, 저장된 issue fixture를 확인합니다.
- pgvector ranking이 적용되지 않음: `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`와 `schema-postgres-pgvector.sql` 적용 여부를 확인합니다.
- AI 응답이 fixture처럼 보임: `AI_PROVIDER`, provider key, smoke mode가 dry-run인지 확인합니다.
- extension live smoke 실패: Chrome profile 로그인 상태, backend URL, `EXTENSION_GITHUB_LIVE_AUTH_HEADER`를 확인합니다.

### Upgrading from earlier versions

기존 로컬 DB를 계속 사용할 때는 schema와 provider env가 함께 바뀌었는지 확인합니다.

1. `back/src/main/resources/schema.sql` 기본 스키마를 확인합니다.
2. PostgreSQL/pgvector 환경에서는 `back/src/main/resources/schema-postgres-pgvector.sql` 적용 순서를 확인합니다.
3. embedding dimension 변경 시 기존 profile/issue embedding backfill을 다시 실행합니다.
4. frontend build arg로 굽히는 `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_BACKEND_OAUTH_URL`을 배포 도메인과 맞춥니다.

<!-- DEPLOYMENT -->

## Deployment

### Docker

backend와 frontend는 각각 Docker image로 빌드합니다. frontend image는 `NEXT_PUBLIC_API_URL`과 `NEXT_PUBLIC_BACKEND_OAUTH_URL`을 build arg로 굽기 때문에 domain 값을 먼저 확정해야 합니다.

```bash
bash deploy/oci/build-aquila-buddy-images.sh /opt/aquila-buddy/aquila-buddy.env
```

### OCI

OCI A1 인스턴스에서는 Docker container와 host Nginx reverse proxy를 사용합니다. 서버는 `/var/lib/aquila-data`를 Docker data-root로 사용하고, 애플리케이션은 loopback port에만 바인딩합니다.

```bash
sudo mkdir -p /opt/aquila-buddy/secrets
sudo cp deploy/oci/aquila-buddy.env.example /opt/aquila-buddy/aquila-buddy.env
sudo editor /opt/aquila-buddy/aquila-buddy.env
bash deploy/oci/run-aquila-buddy.sh /opt/aquila-buddy/aquila-buddy.env
bash deploy/oci/install-nginx-config.sh /opt/aquila-buddy/aquila-buddy.env
```

### Runtime smoke

배포 자산과 외부 provider smoke는 별도 script로 검증합니다.

```bash
bash deploy/oci/validate-buddy-deploy-assets.sh
bash tools/smoke/validate-ai-provider-smoke-env.sh
bash tools/smoke/run-ai-provider-smoke.sh
bash tools/smoke/validate-langfuse-observability-env.sh
bash tools/smoke/run-langfuse-observability-smoke.sh
bash tools/smoke/validate-provider-e2e-env.sh
bash tools/smoke/run-provider-e2e-smoke.sh
```

<!-- ROADMAP -->

## Roadmap

- GitHub OAuth profile sync와 HttpOnly refresh token session 안정화
- pgvector 기반 profile/issue similarity ranking 운영 검증
- repository tree/code search 기반 issue guide 품질 강화
- PR draft 품질 gate, markdown parser fallback, `close #issue` 형식 검증
- Langfuse trace, token usage, latency, 실패 사유 관측 강화
- Chrome extension GitHub DOM badge/sidebar/autofill live smoke 확장
- OCI 배포 smoke와 release readiness gate 분리

<!-- LICENSE -->

## License

이 저장소에는 아직 별도 `LICENSE` 파일이 없습니다. 코드 사용, 재배포, 상업적 활용이 필요하면 저장소 소유자의 정책을 먼저 확인해야 합니다.

## Repo Activity

- 작업 브랜치는 `main`에서 짧게 분기하고 PR base는 `main`으로 둡니다.
- GitHub issue/PR 제목은 `[Type] 한국어 작업 요약` 형식을 사용합니다.
- PR 리뷰와 backend/frontend/extension/operations CI 통과 후 `main`에 병합합니다.
- `main` merge SHA는 `OCI Buddy CD` workflow로 운영 host에 자동 배포합니다.
- live provider, Langfuse, GitHub DOM smoke는 secret과 외부 인증 상태가 있는 opt-in workflow에서 evidence를 남깁니다.

<!-- CONTRIBUTING -->

## Contributing

기여는 이슈 기반으로 진행합니다. 작업 전 GitHub issue를 만들거나 기존 issue 번호를 확정하고, `main` 최신 기준에서 짧은 branch를 분기합니다.

1. Issue를 선택합니다.
2. `type/short-description` 형식의 branch를 만듭니다.
3. 변경 범위와 검증 방법을 계획서에 고정합니다.
4. 관련 검증을 실행합니다.
5. PR 본문 첫 섹션에 `close #<issue-number>`를 연결합니다.

### Good First Issues

작게 시작하려면 [open issues](https://github.com/AquilaXk/aquila-buddy/issues)를 확인하세요. 문서, smoke script, frontend parity validator, fixture test는 진입 비용이 낮은 편입니다.

<!-- INTEGRATIONS -->

## Integrations

### GitHub OAuth and API

- OAuth login: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`
- issue/repository search: `GITHUB_TOKEN`
- 권장 token 권한: `metadata:read,contents:read,pull-requests:read,issues:read`
- local callback: `http://localhost:3000/oauth/callback`
- production callback: `https://buddy.aquilaxk.site/oauth/callback`

### AI Providers

- `AI_PROVIDER=vertex-ai`: Google Vertex AI Gemini completion/embedding provider를 사용합니다.
- `AI_PROVIDER=openai-compatible`: OpenAI-compatible chat completion/embedding provider를 사용합니다.
- `AI_REQUEST_TIMEOUT_MILLIS`: provider 호출 timeout을 지정합니다.
- `AI_EMBEDDING_DIMENSIONS=1536`: pgvector 운영 스키마와 맞춰야 합니다.

### Langfuse

Langfuse tracing은 선택 기능입니다. 운영에서 켤 때만 `LANGFUSE_ENABLED=true`와 public/secret key를 함께 주입하고, smoke는 별도 validation script로 확인합니다.

```bash
LANGFUSE_ENABLED=true
LANGFUSE_BASE_URL=https://cloud.langfuse.com
LANGFUSE_PUBLIC_KEY=
LANGFUSE_SECRET_KEY=
LANGFUSE_ENVIRONMENT=production
```

### Browser Extension

Chrome extension은 GitHub issue/PR 화면에서 badge, sidebar, PR autofill 보조 흐름을 제공합니다. 기본 CI는 manifest/popup/contract를 확인하고, 실제 GitHub DOM smoke는 로그인된 Chrome profile과 live backend auth header가 있을 때만 실행합니다.

```bash
npm --prefix extension run validate
npm --prefix extension run test:e2e:contracts
EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS=30000 \
EXTENSION_E2E_CHROME_EXTRA_FLAGS="--enable-logging=stderr --v=1" \
npm --prefix extension run test:e2e
```

## Workflows

### Issue recommendation to PR draft

1. GitHub OAuth로 로그인합니다.
2. Profile을 저장하고 profile vector refresh를 확인합니다.
3. Good First Issue 후보를 crawl/search합니다.
4. profile/issue embedding 기반 추천을 확인합니다.
5. issue guide와 pseudo code를 생성합니다.
6. diff와 issue context를 반영한 PR draft를 생성합니다.
7. PR draft translation과 submission bundle을 확인합니다.

### Provider E2E smoke

실제 또는 staging key가 있을 때 OAuth-protected API, GitHub, pgvector, AI provider 흐름을 한 번에 검증합니다.

```bash
PROVIDER_E2E_SMOKE_ENABLED=true \
PROVIDER_E2E_DRY_RUN=false \
BACKEND_BASE_URL=https://buddy.aquilaxk.site \
PROVIDER_E2E_AUTH_HEADER="Bearer replace-with-token" \
bash tools/smoke/run-provider-e2e-smoke.sh
```

### Release readiness

release readiness record에는 secret 값을 남기지 않습니다. pass/blocked evidence, 실행 명령, 대상 commit, 남은 risk만 기록합니다.

<!-- ACKNOWLEDGEMENTS -->

## Acknowledgements

- Java, Spring, Next.js, PostgreSQL, pgvector 생태계
- GitHub API와 Actions 기반 오픈소스 협업 도구
- AI provider와 관측성 도구를 안전하게 격리하는 adapter pattern
