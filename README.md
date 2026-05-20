# Aquila Buddy

> GitHub issue 추천, 코드 맥락 분석, PR 초안 작성을 한 흐름으로 묶는 오픈소스 기여 도우미입니다.

<p align="center">
  <a href="https://github.com/AquilaXk/aquila-buddy">
    <img src="front/public/brand-mascot.png" alt="Aquila Buddy" width="150" />
  </a>
</p>

<p align="center">
  <a href="https://buddy.aquilaxk.site">
    <img src="https://img.shields.io/badge/website-Aquila--Buddy-c9ad63?style=for-the-badge" alt="Live site" />
  </a>
  &nbsp;
  <a href="https://github.com/AquilaXk/aquila-buddy">
    <img src="https://img.shields.io/badge/GitHub-aquila--buddy-181717?style=for-the-badge&logo=github" alt="GitHub" />
  </a>
</p>

<p align="center">
  <a href="https://github.com/AquilaXk/aquila-buddy/actions/workflows/ci.yml">
    <img src="https://github.com/AquilaXk/aquila-buddy/actions/workflows/ci.yml/badge.svg" alt="CI" />
  </a>
  <a href="https://github.com/AquilaXk/aquila-buddy/actions/workflows/provider-e2e-smoke.yml">
    <img src="https://github.com/AquilaXk/aquila-buddy/actions/workflows/provider-e2e-smoke.yml/badge.svg" alt="Provider E2E Smoke" />
  </a>
  <a href="https://github.com/AquilaXk/aquila-buddy/actions/workflows/extension-github-live-smoke.yml">
    <img src="https://github.com/AquilaXk/aquila-buddy/actions/workflows/extension-github-live-smoke.yml/badge.svg" alt="Extension GitHub Live Smoke" />
  </a>
  <img src="https://img.shields.io/badge/Java-21-007396?style=flat-square" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring_Boot-3.5.9-6DB33F?style=flat-square" alt="Spring Boot 3.5.9" />
  <img src="https://img.shields.io/badge/Next.js-16.2.6-black?style=flat-square" alt="Next.js 16.2.6" />
  <img src="https://img.shields.io/badge/PostgreSQL-pgvector-336791?style=flat-square" alt="PostgreSQL pgvector" />
</p>

---

## 목차

- [프로젝트 개요](#프로젝트-개요)
- [주요 기능](#주요-기능)
- [시스템 아키텍처](#시스템-아키텍처)
- [주요 프로세스 / AI 설계](#주요-프로세스--ai-설계)
- [기술 스택](#기술-스택)
- [시작하기](#시작하기)
- [환경 변수](#환경-변수)
- [데이터와 pgvector](#데이터와-pgvector)
- [API 명세](#api-명세)
- [테스트와 Smoke](#테스트와-smoke)
- [CI/CD](#cicd)
- [운영과 배포](#운영과-배포)
- [트러블 슈팅](#트러블-슈팅)
- [현재 상태와 개선 방향](#현재-상태와-개선-방향)
- [기여 방식](#기여-방식)
- [License](#license)
- [관련 링크](#관련-링크)

---

## 프로젝트 개요

![Aquila Buddy service preview](front/public/images/aquila-buddy-service-preview.png)

### 배경

오픈소스 입문자는 좋은 이슈를 찾은 뒤에도 repository 구조, 수정 범위, PR 문안까지 직접 판단해야 합니다. Aquila Buddy는 이 과정을 하나의 서비스 흐름으로 묶어 추천, 분석, 초안 작성을 반복 가능하게 만듭니다.

| 문제 | 설명 |
|------|------|
| 이슈 탐색 비용 | Good First Issue 후보가 많아도 사용자 역량과 맞는지 판단하기 어렵습니다. |
| 코드 맥락 부족 | issue metadata만으로는 관련 파일, 수정 방향, side effect를 파악하기 어렵습니다. |
| PR 작성 부담 | contribution guide, issue context, diff context를 반영한 PR 문안을 매번 새로 작성해야 합니다. |
| 운영 검증 난이도 | AI provider, GitHub API, pgvector, extension이 함께 동작하는지 분리해서 확인하기 어렵습니다. |

### 목표

사용자를 대신해 upstream PR을 자동으로 만들거나 병합하지 않습니다. GitHub OAuth로 로그인한 사용자의 profile vector와 GitHub issue vector를 기반으로 추천 순위를 계산하고, 추천 이슈에 대해 코드 맥락 기반 가이드와 PR 초안을 제공합니다.

| 구분 | AS-IS | TO-BE |
|------|-------|-------|
| 이슈 선택 | label과 제목을 수동으로 비교 | profile/issue embedding 기반 추천 |
| 분석 방식 | issue 본문 중심 확인 | repository code context와 관련 파일 기반 가이드 |
| PR 준비 | 빈 editor에서 직접 작성 | issue, diff, template, guide를 반영한 초안 |
| 운영 확인 | 로컬 fixture 중심 확인 | provider, pgvector, Langfuse, extension smoke로 분리 검증 |

### 핵심 가치

- **추천 가능성**: GitHub profile, 관심 topic, language, issue embedding을 추천 입력으로 사용합니다.
- **코드 맥락 우선**: repository tree와 code search 결과를 바탕으로 수정 후보 파일과 side effect를 정리합니다.
- **제출 전 보조**: PR draft와 번역은 사용자가 검토하고 수정하는 초안으로 제공합니다.
- **운영 검증 분리**: secret이 필요한 live smoke와 fixture 기반 CI를 분리해 재현성을 유지합니다.

---

## 주요 기능

| 기능 | 설명 |
|------|------|
| GitHub OAuth 로그인 | GitHub 계정으로 로그인하고 사용자 profile, nickname, email, avatar를 동기화합니다. |
| Profile vector 관리 | 관심 topic, 주 언어, GitHub context를 기반으로 profile embedding을 생성하고 갱신합니다. |
| Good First Issue 수집 | GitHub issue search와 저장된 fixture를 사용해 추천 후보를 수집합니다. |
| pgvector 추천 | PostgreSQL pgvector 환경에서 profile vector와 issue vector 유사도로 추천 순위를 계산합니다. |
| 이슈 코드 분석 | repository tree/code search/contents를 바탕으로 guideline, pseudo code, side effect를 생성합니다. |
| PR 초안 작성 | issue context, analysis context, diff context, PR template을 반영해 title/body 초안을 생성합니다. |
| 번역과 제출 보조 | PR draft를 영문으로 번역하고 submission bundle, readiness, timeline을 제공합니다. |
| Chrome extension | GitHub issue/PR 화면에서 badge, sidebar, PR autofill 보조 흐름을 제공합니다. |
| AI 관측성 | prompt version, model, latency, token usage, 실패 사유를 관측하고 Langfuse 연동 smoke를 제공합니다. |

---

## 시스템 아키텍처

```text
[Browser / Chrome Extension]
  ├─ Next.js App Router frontend
  └─ GitHub issue/PR DOM helper
              │
              ▼
[Spring Boot API]
  ├─ Auth: GitHub OAuth, access token, HttpOnly refresh token
  ├─ User: profile, topics, languages, profile vector
  ├─ Issue: crawl, lookup, recommendation, history
  ├─ Analysis: code context, issue guide, pseudo code
  ├─ PR Draft: draft, translation, readiness, submission
  └─ Observability: AI call records, Langfuse adapter
              │
              ├──────────────► GitHub API
              ├──────────────► Vertex AI or OpenAI-compatible provider
              ├──────────────► Langfuse
              ▼
[PostgreSQL + pgvector]
  ├─ user_profiles
  ├─ refresh_token_sessions
  ├─ recommended_issues
  ├─ issue_guides
  ├─ issue_guide_generation_requests
  ├─ pr_drafts / pr_draft_translations
  └─ ai_call_observations
```

운영 배포는 OCI VM에서 Docker container와 host Nginx reverse proxy를 사용합니다. frontend image는 `NEXT_PUBLIC_API_URL`, `NEXT_PUBLIC_BACKEND_OAUTH_URL`을 build arg로 주입하고 backend는 PostgreSQL, GitHub, AI provider, Langfuse env를 runtime env로 받습니다.

---

## 주요 프로세스 / AI 설계

### 1. 추천에서 PR 초안까지

```text
GitHub OAuth login
  → profile 저장
  → profile vector refresh
  → issue crawl/search
  → pgvector recommendation
  → issue guide / pseudo code 생성
  → diff와 guide context 기반 PR draft 생성
  → translation / submission bundle 확인
```

### 2. Provider 추상화

`AI_PROVIDER` 값으로 provider를 전환합니다. 운영 기본값은 `vertex-ai`이며, OpenAI-compatible smoke에서는 chat completion과 embedding 호출을 같은 흐름에서 검증합니다.

| Provider | 용도 |
|----------|------|
| `vertex-ai` | 운영 기본 Gemini text/embedding provider |
| `openai-compatible` | OpenAI-compatible chat completion, embedding smoke |
| `fixture` | secret 없이 실행 가능한 local/CI fixture 검증 |

### 3. 코드 맥락 분석

이슈 가이드는 issue metadata만 사용하지 않습니다. GitHub repository tree, code search, contents 결과를 이용해 관련 파일을 선별하고, file path, pseudo code, side effect를 별도 저장합니다.

### 4. 관측성과 품질 방어

AI 호출은 prompt version, model, latency, token usage, 실패 사유를 남깁니다. PR 초안은 markdown 구조, title/body parser fallback, `close #issue` 형식, 빈 응답 방어를 테스트로 고정합니다.

---

## 기술 스택

| 영역 | 사용 기술 |
|------|-----------|
| Frontend | Next.js 16.2.6, React 19, TypeScript, Tailwind CSS, App Router |
| Backend | Java 21, Spring Boot 3.5.9, Spring Security, Spring Data JPA, WebFlux, Springdoc OpenAPI |
| Database | PostgreSQL 16 with pgvector, H2 test runtime |
| AI | Vertex AI Gemini, OpenAI-compatible API, embedding provider, Langfuse |
| GitHub 연동 | GitHub OAuth, GitHub REST API, issue search, repository contents |
| Extension | Chrome extension manifest, GitHub DOM smoke, PR autofill |
| Infra | OCI VM, Docker, Docker Compose, Nginx |
| CI/CD | GitHub Actions, backend/frontend/extension/pgvector/provider smoke gates |

---

## 시작하기

### 사전 요구사항

- Java 21
- Node.js 22 또는 현재 LTS
- npm
- Docker & Docker Compose
- GitHub OAuth App, 실제 로그인 검증 시
- Chrome 또는 Chromium, extension live smoke 실행 시

### 1. 저장소 클론

```bash
git clone https://github.com/AquilaXk/aquila-buddy.git
cd aquila-buddy
```

### 2. 환경 변수 예시 복사

```bash
cp .env.example .env.local
cp back/.env.example back/.env.local
cp front/.env.local.example front/.env.local
```

secret이 들어간 파일은 git에 추가하지 않습니다.

### 3. 프론트엔드 의존성 설치

```bash
npm --prefix front install
```

### 4. H2 local backend 실행

```bash
./back/gradlew -p back bootRun
```

- Backend: `http://localhost:8080`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

### 5. frontend 실행

```bash
NEXT_PUBLIC_API_URL=http://localhost:8080 npm --prefix front run dev
```

- Frontend: `http://localhost:3000`
- GitHub OAuth callback: `http://localhost:3000/oauth/callback`
- Backend OAuth entrypoint: `http://localhost:8080/oauth2/authorization/github`

### 6. PostgreSQL/pgvector 개발 환경

```bash
docker compose up -d postgres
SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun
```

- PostgreSQL/pgvector: `localhost:5432`
- Database: `aquila_buddy`
- User/password: `aquila` / `aquila`

---

## 환경 변수

### 인증과 세션

| 변수 | 설명 |
|------|------|
| `GITHUB_CLIENT_ID` | GitHub OAuth App Client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App Client Secret |
| `OAUTH2_REDIRECT_URI` | OAuth callback URI |
| `JWT_SECRET` | access token 서명 secret |
| `JWT_EXPIRATION` | access token 만료 시간 |
| `CORS_FRONTEND_ALLOWED_ORIGINS` | 허용 frontend origin 목록 |

### GitHub API

| 변수 | 설명 |
|------|------|
| `GITHUB_TOKEN` | issue search, repo contents 조회용 token |
| `GITHUB_TOKEN_PERMISSIONS` | 권장 권한 설명값 |
| `GITHUB_API_BASE_URL` | GitHub API base URL |
| `GITHUB_USER_CONTEXT_ENABLED` | 사용자 GitHub context 수집 활성화 |
| `GITHUB_ISSUE_SEARCH_ENABLED` | live issue search 활성화 |
| `GITHUB_ISSUE_SEARCH_QUERY` | 기본 issue search query |
| `GITHUB_ISSUE_SEARCH_LIMIT` | search 결과 제한 |

### AI provider

| 변수 | 설명 |
|------|------|
| `AI_PROVIDER` | `vertex-ai`, `openai-compatible`, `fixture` |
| `VERTEX_AI_LOCATION` | Vertex AI location |
| `VERTEX_AI_MODEL` | Vertex AI text model |
| `VERTEX_AI_EMBEDDING_MODEL` | Vertex AI embedding model |
| `VERTEX_AI_EMBEDDING_DIMENSIONS` | Vertex AI embedding dimension |
| `VERTEX_AI_CREDENTIALS_PATH` | Vertex service account key path |
| `AI_API_BASE_URL` | OpenAI-compatible API base URL |
| `AI_API_KEY` / `OPENAI_API_KEY` | OpenAI-compatible API key |
| `AI_CHAT_MODEL` | Chat completion model |
| `AI_EMBEDDING_MODEL` | Embedding model |
| `AI_EMBEDDING_DIMENSIONS` | pgvector schema와 맞출 embedding dimension |
| `AI_REQUEST_TIMEOUT_MILLIS` | provider timeout |

### Database, recommendation, observability

| 변수 | 설명 |
|------|------|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USER` | DB 사용자 |
| `DB_PASSWORD` | DB 비밀번호 |
| `RECOMMENDATION_SIMILARITY_PROVIDER` | `score` 또는 `pgvector` |
| `RECOMMENDATION_SIMILARITY_LIMIT` | 추천 결과 제한 |
| `LANGFUSE_ENABLED` | Langfuse 연동 활성화 |
| `LANGFUSE_BASE_URL` | Langfuse endpoint |
| `LANGFUSE_PUBLIC_KEY` | Langfuse public key |
| `LANGFUSE_SECRET_KEY` | Langfuse secret key |
| `LANGFUSE_ENVIRONMENT` | Langfuse environment label |

---

## 데이터와 pgvector

기본 `schema.sql`은 H2/local 실행과 공통 테이블 생성에 사용됩니다. PostgreSQL 운영 환경에서는 `schema-postgres-pgvector.sql`을 추가 적용해 embedding 컬럼을 `vector(1536)`으로 정규화하고 pgvector index를 사용합니다.

| 테이블 | 역할 |
|--------|------|
| `user_profiles` | GitHub 사용자, profile, embedding 저장 |
| `refresh_token_sessions` | HttpOnly refresh token session 저장 |
| `recommended_issues` | 추천 후보 issue와 issue embedding 저장 |
| `issue_guides` | 이슈 가이드, pseudo code, side effect 저장 |
| `issue_guide_generation_requests` | 분석 요청 이력, 제한, 캐시 재사용 기준 |
| `pr_drafts` | PR 초안, readiness, timeline, submission context 저장 |
| `pr_draft_translations` | PR 초안 번역 결과 저장 |
| `ai_call_observations` | AI 호출 관측 event 저장 |

pgvector 통합 검증은 별도 compose 환경에서 실행합니다.

```bash
bash tools/test/run-pgvector-integration.sh --compose --dry-run
PGVECTOR_TEST_DB_PORT=54329 bash tools/test/run-pgvector-integration.sh --compose
```

---

## API 명세

전체 API 명세는 Springdoc Swagger UI에서 확인합니다.

- Local: `http://localhost:8080/swagger-ui.html`
- Health: `GET /api/v1/health`

### 핵심 API 요약

| 도메인 | Method | Endpoint | 설명 |
|--------|--------|----------|------|
| Auth | `POST` | `/api/v1/auth/refresh` | HttpOnly refresh token 기반 access token 재발급 |
| Auth | `POST` | `/api/v1/auth/logout` | refresh token session 종료 |
| OAuth | `GET` | `/oauth2/authorization/github` | GitHub OAuth 로그인 시작 |
| OAuth | `GET` | `/oauth/callback` | frontend callback 보조 endpoint |
| User | `GET` | `/api/v1/users/me` | 현재 사용자 profile 조회 |
| User | `PATCH` | `/api/v1/users/me` | profile topic/language/email 등 갱신 |
| User | `POST` | `/api/v1/users/me/vector` | profile vector refresh |
| Issue | `GET` | `/api/v1/issues/recommend` | 추천 issue 조회 |
| Issue | `POST` | `/api/v1/issues/crawl/search` | GitHub issue search/crawl |
| Analysis | `GET` | `/api/v1/issues/{issueId}/guide` | 이슈 수정 가이드 조회 |
| Analysis | `GET` | `/api/v1/issues/{issueId}/pseudo` | pseudo code 조회 |
| PR Draft | `POST` | `/api/v1/pr` | PR 초안 생성 |
| PR Draft | `GET` | `/api/v1/pr/{id}/submission` | 제출 보조 bundle 조회 |
| PR Draft | `POST` | `/api/v1/pr/{id}/translate` | PR 초안 번역 |
| AI | `GET` | `/api/v1/ai/observability` | AI 호출 관측 결과 조회 |
| Embedding | `POST` | `/api/v1/embeddings/backfill` | embedding backfill 운영 API |

---

## 테스트와 Smoke

기본 검증은 secret 없이 실행 가능한 unit, contract, fixture smoke로 구성됩니다.

```bash
./back/gradlew -p back check
npm --prefix front run typecheck
npm --prefix front run build
npm --prefix front run test:e2e
npm --prefix front run smoke:integration
npm --prefix extension run validate
npm --prefix extension run test:e2e:contracts
```

### Provider E2E smoke

실제 또는 staging key가 있을 때 OAuth-protected API, GitHub, pgvector, AI provider 흐름을 한 번에 검증합니다.

```bash
PROVIDER_E2E_SMOKE_ENABLED=true \
PROVIDER_E2E_DRY_RUN=false \
BACKEND_BASE_URL=https://buddy.aquilaxk.site \
PROVIDER_E2E_AUTH_HEADER="Bearer replace-with-token" \
bash tools/smoke/run-provider-e2e-smoke.sh
```

### OpenAI-compatible smoke

```bash
AI_PROVIDER=openai-compatible \
AI_API_BASE_URL=https://api.openai.com/v1 \
AI_API_KEY=openai_or_compatible_api_key \
AI_CHAT_MODEL=gpt-4o-mini \
AI_EMBEDDING_MODEL=text-embedding-3-small \
AI_EMBEDDING_DIMENSIONS=1536 \
RECOMMENDATION_SIMILARITY_PROVIDER=pgvector \
GITHUB_TOKEN=github_pat_or_fine_grained_token \
bash tools/smoke/validate-ai-runtime-env.sh
```

### Extension smoke

```bash
npm --prefix extension run validate
npm --prefix extension run test:e2e:contracts
EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS=30000 \
EXTENSION_E2E_CHROME_EXTRA_FLAGS="--enable-logging=stderr --v=1" \
npm --prefix extension run test:e2e
```

---

## CI/CD

```text
Pull Request
  ├─ Backend: Gradle check
  ├─ Frontend: typecheck, build, UI/UX parity validators
  ├─ Extension: manifest, popup, contract smoke
  ├─ Pgvector Integration: PostgreSQL + pgvector integration gate
  └─ Operations Smoke: provider/Langfuse/release readiness validation
        │
        ▼
main merge
        │
        ▼
OCI Buddy CD
  ├─ container image build / pull
  ├─ PostgreSQL/pgvector init
  ├─ backend/frontend container run
  ├─ Nginx reverse proxy config render
  └─ runtime smoke
```

문서/이미지처럼 영향 범위가 작은 PR은 변경 파일 기반 CI skip gate를 사용할 수 있도록 release gate 분리를 진행 중입니다.

---

## 운영과 배포

### OCI 배포

OCI A1 인스턴스에서는 Docker container와 host Nginx reverse proxy를 사용합니다. 애플리케이션 container는 loopback port에 바인딩하고 외부 트래픽은 Nginx가 받아 backend/frontend로 전달합니다.

```bash
sudo mkdir -p /opt/aquila-buddy/secrets
sudo cp deploy/oci/aquila-buddy.env.example /opt/aquila-buddy/aquila-buddy.env
sudo editor /opt/aquila-buddy/aquila-buddy.env
bash deploy/oci/run-aquila-buddy.sh /opt/aquila-buddy/aquila-buddy.env
bash deploy/oci/install-nginx-config.sh /opt/aquila-buddy/aquila-buddy.env
```

### 운영 smoke

```bash
bash deploy/oci/validate-buddy-deploy-assets.sh
bash tools/smoke/validate-ai-provider-smoke-env.sh
bash tools/smoke/run-ai-provider-smoke.sh
bash tools/smoke/validate-langfuse-observability-env.sh
bash tools/smoke/run-langfuse-observability-smoke.sh
bash tools/smoke/validate-provider-e2e-env.sh
bash tools/smoke/run-provider-e2e-smoke.sh
```

### 운영 문서

- [AI 운영 smoke runbook](tools/smoke/AI_RUNTIME_SMOKE.md)
- [release readiness records](tools/smoke/release-readiness/README.md)
- [동등성 완료 매트릭스](tools/smoke/release-readiness/PARITY_COMPLETION_MATRIX.md)
- [OCI 배포 문서](deploy/oci/README.md)

---

## 트러블 슈팅

### 1. 로그인 후 profile 정보가 비어 있음

| | 내용 |
|-|------|
| 문제 | GitHub OAuth 로그인 후 nickname, email, avatar가 기대와 다르게 표시됩니다. |
| 확인 | GitHub OAuth scope, `/api/v1/users/me` 응답, GitHub email 공개 여부를 확인합니다. |
| 조치 | OAuth profile upsert, email fallback, avatar URL 저장 경로를 확인하고 profile page에서 저장 후 새로고침합니다. |

### 2. 추천 결과가 비어 있음

| | 내용 |
|-|------|
| 문제 | `/api/v1/issues/recommend` 결과가 비어 있습니다. |
| 확인 | `GITHUB_ISSUE_SEARCH_ENABLED`, `GITHUB_TOKEN_PERMISSIONS`, 저장된 issue fixture, profile vector 상태를 확인합니다. |
| 조치 | issue crawl/search를 실행하고 `RECOMMENDATION_SIMILARITY_PROVIDER`가 `score` 또는 `pgvector`로 맞는지 확인합니다. |

### 3. pgvector ranking이 적용되지 않음

| | 내용 |
|-|------|
| 문제 | 추천이 vector 유사도 기반으로 정렬되지 않습니다. |
| 확인 | `schema-postgres-pgvector.sql` 적용 여부, `AI_EMBEDDING_DIMENSIONS=1536`, backfill 상태를 확인합니다. |
| 조치 | PostgreSQL/pgvector profile로 실행하고 embedding backfill 후 통합 테스트를 다시 실행합니다. |

### 4. AI 응답이 fixture처럼 보임

| | 내용 |
|-|------|
| 문제 | 실제 provider key를 넣었는데도 fixture 응답이 반환됩니다. |
| 확인 | `AI_PROVIDER`, provider key, smoke dry-run 여부, timeout 설정을 확인합니다. |
| 조치 | `AI_PROVIDER=openai-compatible` 또는 `vertex-ai`로 전환하고 runtime env validation script를 먼저 실행합니다. |

### 5. Chrome extension live smoke 실패

| | 내용 |
|-|------|
| 문제 | GitHub DOM에서 badge, sidebar, autofill이 표시되지 않습니다. |
| 확인 | Chrome profile 로그인 상태, backend URL, `EXTENSION_GITHUB_LIVE_AUTH_HEADER`, GitHub page fixture를 확인합니다. |
| 조치 | manifest/popup contract 검증을 먼저 통과시킨 뒤 live smoke를 opt-in으로 실행합니다. |

---

## 현재 상태와 개선 방향

| 영역 | 현재 상태 | 개선 방향 |
|------|-----------|-----------|
| OAuth/Profile | GitHub OAuth, user profile, refresh token session 경로 구현 | private email, avatar, session 유지 live smoke 강화 |
| Recommendation | fixture와 pgvector 추천 경로 제공 | backfill, 차원 불일치 관측, 추천 순위 regression 고정 |
| Issue Guide | GitHub code search/contents 기반 context 제공 | 관련 파일 선별 품질, truncation, fallback 품질 평가 강화 |
| PR Draft | draft, translation, readiness, submission 흐름 제공 | markdown parser fallback과 close issue 품질 gate 강화 |
| Observability | AI call observation과 Langfuse adapter 제공 | 실제 Langfuse trace, usage, error metadata 실연동 검증 |
| Extension | manifest/popup/contract 검증 제공 | 실제 GitHub DOM live smoke evidence 강화 |
| CI/CD | backend/frontend/extension/operations gate 제공 | 변경 파일 기반 skip과 release readiness gate 분리 |

---

## 기여 방식

기여는 이슈 기반으로 진행합니다. 작업 전 GitHub issue를 만들거나 기존 issue 번호를 확정하고, `main` 최신 기준에서 짧은 branch를 분기합니다.

1. Issue를 선택합니다.
2. `type/short-description` 형식의 branch를 만듭니다.
3. 변경 범위와 검증 방법을 계획서에 고정합니다.
4. 관련 검증을 실행합니다.
5. PR 본문 첫 섹션에 `close #<issue-number>`를 연결합니다.

작게 시작하려면 [open issues](https://github.com/AquilaXk/aquila-buddy/issues)를 확인하세요. 문서, smoke script, frontend parity validator, fixture test는 진입 비용이 낮은 편입니다.

---

## License

이 저장소에는 현재 branch 기준 별도 `LICENSE` 파일이 없습니다. 코드 사용, 재배포, 상업적 활용이 필요하면 저장소 소유자의 정책을 먼저 확인해야 합니다.

---

## 관련 링크

| 구분 | URL |
|------|-----|
| 서비스 | [buddy.aquilaxk.site](https://buddy.aquilaxk.site) |
| GitHub | [AquilaXk/aquila-buddy](https://github.com/AquilaXk/aquila-buddy) |
| Issues | [GitHub Issues](https://github.com/AquilaXk/aquila-buddy/issues) |
| Pull Requests | [GitHub Pull Requests](https://github.com/AquilaXk/aquila-buddy/pulls) |
| CI | [GitHub Actions](https://github.com/AquilaXk/aquila-buddy/actions) |
| AI Smoke | [AI_RUNTIME_SMOKE.md](tools/smoke/AI_RUNTIME_SMOKE.md) |
| Deploy | [deploy/oci/README.md](deploy/oci/README.md) |
