# Aquila Buddy

개인 프로젝트 기준의 오픈소스 기여 도우미 MVP입니다. GitHub OAuth 로그인, 개발자 프로필 분석, Good First Issue 추천, 이슈 해결 힌트, PR 초안 작성 흐름을 작은 운영 예산 안에서 검증하는 데 초점을 둡니다.
실제 자동 기여 대행 서비스가 아니라 `GitHub API 연동`, `pgvector 기반 추천`, `AI provider boundary`, `PR draft workflow`, `Chrome extension live smoke`, `OCI A1 운영 budget`, `회귀 방지 gate`를 포트폴리오 수준으로 보여주는 프로젝트입니다.

![Aquila Buddy mascot](front/public/brand-mascot.png)

## 프로젝트 개요

Aquila Buddy는 모든 오픈소스 이슈를 자동 해결하는 프로젝트가 아닙니다. GitHub issue와 repository context를 수집하고, 현재 사용자의 profile embedding과 issue embedding을 기준으로 후보를 좁힌 뒤, 이슈 이해와 PR 작성에 필요한 보조 정보를 제공합니다.

- 프런트는 `Next.js 16 + React 19 + TypeScript`, 백엔드는 `Spring Boot 3.5 + Java 21`로 구성했습니다.
- 데이터 기준은 `PostgreSQL + pgvector`이고, 로컬 빠른 smoke는 H2 기본값으로 시작할 수 있습니다.
- AI 경로는 `Vertex AI`와 `OpenAI-compatible` provider boundary 뒤에 두고, fixture/dry-run/live smoke를 분리했습니다.
- 운영 경로는 `Nginx reverse proxy`, Docker image, GitHub Actions CI/CD, OCI A1 self-hosted runner 기준으로 정리했습니다.

### 실제 기여 보조 흐름

사용자는 GitHub로 로그인한 뒤 프로필을 준비하고, 추천 이슈 목록에서 상세 내용을 확인합니다. 이후 수정 가이드와 의사 코드를 참고해 작업 방향을 잡고, PR 초안/영문 번역을 생성해 upstream 제출 전 문안을 다듬습니다.

## 개인 프로젝트 범위

### In Scope

- GitHub OAuth 로그인, JWT cookie/session 기반 인증
- GitHub 사용자 프로필 저장, profile vector refresh
- GitHub issue 검색, Good First Issue 후보 수집/저장
- PostgreSQL pgvector 기반 profile/issue similarity ranking
- 이슈 상세, 수정 가이드, 의사 코드, workflow readiness
- PR 초안 생성, PR 초안 이력, 제출 readiness, 영문 번역
- AI 호출 관측, Langfuse 선택 전송, release readiness evidence
- Chrome extension 검증과 GitHub live smoke opt-in 흐름
- OCI A1 단일 host 배포 baseline과 public health 검증

### Out of Scope

- 사용자를 대신한 upstream PR 자동 생성/merge
- 임의 repository write 권한 사용
- GitHub marketplace app, 조직 단위 multi-tenant SaaS 운영
- 결제, 과금, 팀 관리, 관리자 콘솔
- 무제한 issue crawl, 전역 검색, 대규모 organization indexing
- provider secret 관리형 vault, 상용 SLA, 고가용성 multi-region 배포

이 프로젝트는 기여 준비와 제출 문안 작성을 보조하는 데모 범위입니다. 실제 upstream 변경 생성, reviewer 대응, maintainer 승인, 배포 책임은 사용자의 로컬 개발 흐름과 대상 저장소 정책에 남겨둡니다.

## 왜 이 프로젝트가 차별점이 있는가

- `기여 workflow`를 화면 단위 기능보다 앞에 두었습니다.
  - 프로필 준비, 이슈 추천, 분석 힌트, PR 초안, 번역, 제출 readiness가 한 흐름으로 이어지도록 구성합니다.
- `AI 호출을 운영 경계 뒤에 둡니다`.
  - provider별 구현은 adapter로 분리하고, 실패 시 fallback과 smoke evidence를 남깁니다.
- `추천 근거를 데이터 경로로 검증합니다`.
  - profile embedding, issue embedding, pgvector ranking, score fallback을 분리해 추천 API 전체 실패를 줄입니다.
- `브라우저 확장과 웹 앱을 함께 검증합니다`.
  - 기본 CI는 fixture/contract를 확인하고, 실제 GitHub DOM smoke는 로그인된 Chrome profile이 있는 opt-in 경로로 둡니다.
- `성능/완료 주장을 evidence와 분리합니다`.
  - parity matrix, release readiness record, live provider smoke가 없는 상태에서는 완료/운영 준비를 과장하지 않습니다.

## 핵심 기능

### GitHub Profile & Auth

- GitHub OAuth 로그인과 OAuth callback profile upsert
- JWT access token cookie 기반 API 인증
- 사용자 이름, email, 감지 언어, topic 저장
- profile embedding refresh와 갱신 시각 추적

### Issue Recommendation

- GitHub Search API 기반 Good First Issue 후보 수집
- 수집 issue 저장, history 조회, 상세 조회
- issue body summary 정리, label/status/score 보존
- pgvector similarity ranking과 score fallback

### Issue Analysis

- repository code context 수집
- 수정 가이드, 의사 코드, side effect 정리
- guide cache, issue updated-at 비교, 일일 생성 제한
- 분석 결과를 PR 초안 context로 재사용

### PR Draft Workflow

- base/head branch, issue context, diff context 기반 PR 초안 생성
- GitHub compare diff 실패 시 fallback context 초안 생성
- PR 초안 history, readiness, timeline, submission bundle
- 영문 번역 draft와 contribution report

### Runtime Smoke & Extension

- AI provider dry-run/live smoke
- Langfuse observability dry-run/live smoke
- provider E2E smoke와 release readiness record
- Chrome extension manifest/popup/live E2E contract 검증

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| Frontend | Next.js 16, React 19, TypeScript, Tailwind CSS |
| Backend | Spring Boot 3.5, Java 21, Spring Security, OAuth2 Client, JPA, WebFlux |
| Data | PostgreSQL, pgvector, H2 local smoke |
| AI / Observability | Vertex AI, OpenAI-compatible API, Langfuse |
| Browser Extension | Chrome extension, Node test runner, Chrome DevTools live smoke |
| Edge / Ops | Nginx, Docker, OCI A1 Flex, GitHub Actions self-hosted runner |
| Quality | Gradle check, ArchUnit, MockWebServer, frontend parity validators, integration smoke |

## 아키텍처 포인트

### 1. Hexagonal Boundary

- `domain`은 Spring/JPA/web/sdk 구현에 의존하지 않습니다.
- `application`은 use case와 outbound port를 조합합니다.
- `adapter/in`은 REST API, `adapter/out`은 persistence/GitHub/AI/Langfuse 구현을 담당합니다.
- 인증, 예외, 응답 envelope, provider 설정은 `global` 경계에 둡니다.

### 2. Provider Boundary

- PR 초안, issue guide, 번역, embedding은 AI provider adapter 뒤에 있습니다.
- `vertex-ai`와 `openai-compatible` provider를 runtime env로 선택합니다.
- provider 장애나 parser 실패는 fallback draft/guide와 observation으로 흡수합니다.

### 3. Recommendation Read Path

- online 추천은 저장된 issue 후보와 사용자 profile embedding을 기준으로 좁힙니다.
- pgvector가 비활성화되거나 ranking이 비면 score 기반 fallback으로 응답합니다.
- issue crawl/search 실패가 추천 API 전체 실패로 번지지 않도록 저장 데이터 fallback을 유지합니다.

### 4. Evidence-Based Runtime

- 기본 CI는 backend/frontend/extension/operations smoke/pgvector integration을 분리해 실행합니다.
- live provider, Langfuse, GitHub DOM smoke는 secret과 외부 인증 상태가 필요하므로 opt-in으로 둡니다.
- release readiness record는 secret 없이 pass/blocked evidence만 남깁니다.

## 프로젝트 구조

```text
.
├── back/                   # Spring Boot 3.5 / Java 21 backend
├── front/                  # Next.js 16 / React 19 frontend
├── extension/              # Chrome extension surface
├── deploy/oci/             # OCI A1 Docker/Nginx deploy scripts
├── infra/terraform/        # OCI Always Free A1 baseline
├── tools/                  # smoke/test/frontend/extension helper scripts
├── .github/                # issue/PR templates, CI/CD workflows
├── compose.yaml            # local PostgreSQL/pgvector 개발 인프라
└── README.md               # project overview and operation entrypoint
```

## 로컬 실행

### 사전 준비

- Java 21
- Node.js LTS
- npm
- Docker Compose
- Chrome 또는 Chromium, extension live smoke 실행 시

### 1. 백엔드 빠른 실행

기본 실행은 H2 기반 local smoke에 맞춰져 있습니다.

```bash
./back/gradlew -p back bootRun
```

- Backend: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 2. PostgreSQL/pgvector 개발 인프라 실행

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun
```

- PostgreSQL/pgvector: `localhost:5432`
- Database: `aquila_buddy`
- User/password: `aquila` / `aquila`

### 3. 프런트 실행

```bash
npm --prefix front install
NEXT_PUBLIC_API_URL=http://localhost:8080 npm --prefix front run dev
```

다른 포트를 쓰려면 `PORT`를 함께 지정합니다.

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080 PORT=3001 npm --prefix front run dev
```

- Front: `http://localhost:3000`

로그인, 프로필, 추천, PR 초안 API 호출은 백엔드가 먼저 떠 있어야 합니다. 백엔드 연결 오류가 보이면 다음 순서로 확인합니다.

```bash
curl http://localhost:8080/actuator/health
```

1. `./back/gradlew -p back bootRun` 또는 `SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun`이 실행 중인지 확인합니다.
2. 프런트 실행 shell에 `NEXT_PUBLIC_API_URL=http://localhost:8080`이 들어갔는지 확인합니다.
3. PostgreSQL profile을 사용할 때는 `docker compose up -d postgres`가 먼저 실행됐는지 확인합니다.

### 4. Extension 검증

```bash
npm --prefix extension run validate
npm --prefix extension run test:e2e:contracts
```

실제 GitHub DOM smoke는 로그인된 Chrome profile, live backend URL, 인증 header, 대상 issue/compare URL이 있을 때만 opt-in으로 실행합니다.

## 품질 게이트

### Backend

```bash
./back/gradlew -p back check
```

- unit/slice test, Spring context test, persistence integration, ArchUnit architecture test를 포함합니다.
- pgvector 통합 검증은 별도 script로 격리된 PostgreSQL/pgvector 컨테이너를 사용할 수 있습니다.

```bash
bash tools/test/run-pgvector-integration.sh --compose --dry-run
PGVECTOR_TEST_DB_PORT=54329 bash tools/test/run-pgvector-integration.sh --compose
```

### Frontend

```bash
npm --prefix front run typecheck
npm --prefix front run build
npm --prefix front run test:e2e
npm --prefix front run smoke:integration
```

프런트 smoke는 기본 CI에서 fixture backend를 띄워 보호 라우트, success envelope, failure envelope를 브라우저로 확인합니다.
CI release gate처럼 `npm run build` 산출물을 검증할 때는 `next start` 모드를 사용합니다.

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

### Extension

```bash
npm --prefix extension run validate
npm --prefix extension run test:e2e:contracts
```

Chrome DevTools endpoint를 사용하는 live E2E는 runner 환경에서 Chrome startup timeout과 stderr logging을 명시합니다.

```bash
EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS=30000 \
EXTENSION_E2E_CHROME_EXTRA_FLAGS="--enable-logging=stderr --v=1" \
npm --prefix extension run test:e2e
```

백엔드 credential CORS는 extension origin을 `CORS_EXTENSION_ALLOWED_ORIGINS`로 제한합니다. 로컬 기본값은 개발 편의를 위해 `chrome-extension://*`를 허용하지만, `prod`/`staging` profile에서는 실제 extension id 기반 origin만 사용할 수 있습니다.

### Runtime Smoke

```bash
bash tools/smoke/validate-ai-runtime-env.sh
bash tools/smoke/validate-ai-provider-smoke-env.sh
bash tools/smoke/run-ai-provider-smoke.sh
bash tools/smoke/validate-langfuse-observability-env.sh
bash tools/smoke/run-langfuse-observability-smoke.sh
bash tools/smoke/validate-provider-e2e-env.sh
bash tools/smoke/run-provider-e2e-smoke.sh
```

필수 env가 빠진 상태에서는 validation script가 실패해야 합니다. live smoke는 외부 provider key와 인증 상태가 필요하므로 기본 CI에서는 실행하지 않습니다.

## Runtime Baseline

- public domain: `https://buddy.aquilaxk.site`
- backend runtime: Spring Boot 3.5 / Java 21
- frontend runtime: Next.js 16 / Node.js 22 container
- database: PostgreSQL with pgvector
- AI provider default: `vertex-ai`
- recommendation ranking: `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`
- OCI baseline: A1 Flex single host, Docker data-root on `/var/lib/aquila-data`
- loopback ports: frontend `127.0.0.1:13011`, backend `127.0.0.1:18091`

## Environment Split

- 로컬 빠른 개발: H2 기본 backend + local frontend
- 로컬 pgvector 개발: `compose.yaml` PostgreSQL/pgvector + `SPRING_PROFILES_ACTIVE=dev`
- AI provider dry-run: smoke script validation과 synthetic payload 확인
- AI provider live: 실제 provider key와 timeout/error observation 확인
- 운영 배포: OCI A1 Docker images + host Nginx reverse proxy
- release evidence: [release readiness records](tools/smoke/release-readiness/README.md)

## Runtime Configuration

기본 운영 AI provider는 `vertex-ai`입니다.

- `AI_PROVIDER=vertex-ai`: Google Vertex AI Gemini completion/embedding provider를 사용합니다.
- `AI_PROVIDER=openai-compatible`: OpenAI-compatible chat completion/embedding provider를 사용합니다.
- `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`: 사용자 profile embedding과 issue embedding의 pgvector similarity ranking을 사용합니다.
- `LANGFUSE_ENABLED=true`: AI 호출을 Langfuse generation event로 함께 전송합니다. 기본값은 `false`입니다.
- `ANALYSIS_ISSUE_GUIDE_DAILY_LIMIT=5`: 사용자별 이슈 분석 guide 생성 일일 제한입니다. cache hit는 quota에 반영하지 않습니다.

OpenAI-compatible smoke를 실행할 때는 provider, GitHub token 권한, pgvector 설정을 한 번에 검증합니다.

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

Langfuse tracing은 선택 기능입니다. 운영에서 켤 때만 `LANGFUSE_ENABLED=true`와 public/secret key를 함께 주입하고, smoke는 별도 validation script로 확인합니다.

GitHub 연동에는 repository/issue 조회가 가능한 fine-grained token이 필요합니다. OAuth 로그인을 함께 확인할 때는 GitHub OAuth app의 Authorization callback URL을 운영 도메인 callback과 맞춥니다.

credential 파일과 secret은 로컬 또는 운영 private path에만 둡니다. `vertex-key.json`, OAuth secret, JWT secret, provider key, GitHub token은 git에 추가하지 않습니다.

## OCI Runtime Deploy

OCI A1 인스턴스에서 aquila-buddy를 실행할 때는 Docker image와 host Nginx 라우팅을 사용합니다. 서버는 `/var/lib/aquila-data`를 Docker data-root로 사용하고, 애플리케이션은 기존 80/443 포트를 직접 점유하지 않고 loopback 포트로만 바인딩합니다.

배포 env는 예시 파일을 private path로 복사해 채웁니다.

```bash
sudo mkdir -p /opt/aquila-buddy/secrets
sudo cp deploy/oci/aquila-buddy.env.example /opt/aquila-buddy/aquila-buddy.env
sudo editor /opt/aquila-buddy/aquila-buddy.env
```

이미지를 빌드하고 registry에 push할 때는 `AQUILA_BUDDY_PUSH_IMAGES=true`를 사용합니다. frontend image는 `NEXT_PUBLIC_API_URL`과 `NEXT_PUBLIC_BACKEND_OAUTH_URL`을 build arg로 굽기 때문에 domain 값을 먼저 확정해야 합니다.

```bash
bash deploy/oci/build-aquila-buddy-images.sh /opt/aquila-buddy/aquila-buddy.env
bash deploy/oci/run-aquila-buddy.sh /opt/aquila-buddy/aquila-buddy.env
bash deploy/oci/install-nginx-config.sh /opt/aquila-buddy/aquila-buddy.env
```

배포 자산의 정적 검증은 다음 명령으로 실행합니다.

```bash
bash deploy/oci/validate-buddy-deploy-assets.sh
```

## Delivery Flow

- 작업 브랜치는 `main`에서 짧게 분기하고 PR base는 `main`으로 둡니다.
- GitHub issue/PR 제목은 `[Type] 한국어 작업 요약` 형식을 사용합니다.
- PR 리뷰와 backend/frontend/extension/operations CI 통과 후 `main`에 병합합니다.
- `main` merge SHA는 `OCI Buddy CD` workflow로 운영 host에 자동 배포합니다.
- live provider, Langfuse, GitHub DOM smoke는 secret과 외부 인증 상태가 있는 opt-in workflow에서 evidence를 남깁니다.
- 미완성 기능은 장기 `develop` 브랜치 대신 feature flag 또는 runtime env로 기본 비노출 처리합니다.

## 관련 문서

- OCI 배포 가이드: [deploy/oci/README.md](deploy/oci/README.md)
- OCI Always Free baseline: [infra/terraform/oci/always-free-a1-flex/README.md](infra/terraform/oci/always-free-a1-flex/README.md)
- [AI 운영 smoke runbook](tools/smoke/AI_RUNTIME_SMOKE.md)
- release readiness records: [tools/smoke/release-readiness/README.md](tools/smoke/release-readiness/README.md)
- 동등성 완료 매트릭스: [tools/smoke/release-readiness/PARITY_COMPLETION_MATRIX.md](tools/smoke/release-readiness/PARITY_COMPLETION_MATRIX.md)

## 프로젝트에서 강조하고 싶은 점

이 프로젝트는 실제 기여를 자동으로 대신 수행하려는 구현이 아니라, 개인 프로젝트 범위에서 오픈소스 기여 준비 흐름을 일관된 제품 경험으로 구성한 포트폴리오입니다.
GitHub API, pgvector 추천, AI draft, Langfuse 관측, Chrome extension smoke, OCI 배포 evidence를 코드와 문서의 같은 레벨에서 관리하되, upstream write 자동화와 상용 SaaS 운영 책임은 명시적으로 범위 밖에 둡니다.
