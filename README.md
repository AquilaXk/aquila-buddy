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
- `AI_PROVIDER=openai-compatible`: OpenAI-compatible chat completion/embedding provider를 사용합니다.
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

OpenAI-compatible smoke에 필요한 기본 env:

```bash
AI_API_BASE_URL=https://api.openai.com/v1
AI_API_KEY=openai_or_compatible_api_key
AI_CHAT_MODEL=gpt-4o-mini
AI_TEMPERATURE=0.2
AI_EMBEDDING_MODEL=text-embedding-3-small
AI_EMBEDDING_DIMENSIONS=1536
AI_REQUEST_TIMEOUT_MILLIS=30000
```

`AI_API_KEY` 대신 `OPENAI_API_KEY`를 사용할 수 있습니다. chat model은 issue guide, PR 초안, 번역에 쓰이고 embedding model은 profile/issue embedding과 pgvector ranking smoke에 쓰입니다.

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

운영 배포 전 상세 점검 절차는 [AI 운영 smoke runbook](tools/smoke/AI_RUNTIME_SMOKE.md)을 기준으로 확인합니다.

PostgreSQL/pgvector와 실제 AI provider를 함께 확인하는 최소 순서:

```bash
docker compose up -d
bash tools/test/run-pgvector-integration.sh --validate-only
bash tools/test/run-pgvector-integration.sh --compose --dry-run
bash tools/smoke/validate-ai-runtime-env.sh
SPRING_PROFILES_ACTIVE=dev ./back/gradlew -p back bootRun
```

필수 env가 빠진 상태에서는 validation script가 실패해야 합니다.

```bash
bash tools/smoke/validate-ai-runtime-env.sh
bash tools/smoke/validate-ai-provider-smoke-env.sh
bash tools/smoke/run-ai-provider-smoke.sh
bash tools/smoke/validate-provider-e2e-env.sh
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
GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read \
bash tools/smoke/validate-ai-runtime-env.sh
```

OpenAI-compatible + pgvector smoke env 예시:

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
DB_URL=jdbc:postgresql://localhost:5432/aquila_buddy \
DB_USER=aquila \
DB_PASSWORD=aquila \
GITHUB_TOKEN=github_pat_or_fine_grained_token \
GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read \
bash tools/smoke/validate-ai-runtime-env.sh
```

수동 smoke 흐름:

1. OAuth 로그인 또는 `DEFAULT_GITHUB_ID` fallback 사용자로 profile을 준비합니다.
2. profile vector refresh를 실행해 `profile_embedding` 저장을 확인합니다.
3. issue crawl/search를 실행해 `issue_embedding` 저장을 확인합니다.
4. `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector`로 추천 순위가 embedding similarity를 반영하는지 확인합니다.
5. issue guide, PR draft, translation을 실행해 AI 호출과 `ai_call_observations` 저장을 확인합니다.
6. `LANGFUSE_ENABLED=true` 환경에서는 같은 호출이 Langfuse generation event로 적재되는지 확인합니다.

pgvector 운영 검증은 `schema.sql` 이후 `schema-postgres-pgvector.sql` 적용 순서, `profile_embedding`/`issue_embedding`의 `vector(1536)` 변환, `idx_user_profiles_profile_embedding`/`idx_recommended_issues_issue_embedding` HNSW index, backfill 후 추천 순위 변경을 확인합니다.

로컬에서 격리된 PostgreSQL + pgvector 컨테이너로 통합 테스트를 실행하려면 `--compose`를 사용합니다. 기본 포트는 `54329`이며 `PGVECTOR_TEST_DB_PORT`로 바꿀 수 있습니다.

```bash
bash tools/test/run-pgvector-integration.sh --compose --dry-run
PGVECTOR_TEST_DB_PORT=54329 bash tools/test/run-pgvector-integration.sh --compose
```

운영 backfill API는 `APP_EMBEDDING_BACKFILL_OPERATION_TOKEN`이 설정된 경우에만 열립니다. 요청에는 `X-Aquila-Operation-Token`과 선택적 `X-Aquila-Operator` header를 함께 넘기며, 성공/거부/실패/rate-limit 결과는 secret 없이 audit log로 남습니다. `EMBEDDING_BACKFILL_MAX_LIMIT`은 요청 limit 상한, `EMBEDDING_BACKFILL_MIN_INTERVAL_SECONDS`는 연속 실행 최소 간격입니다.

실제 provider E2E smoke는 opt-in입니다. OAuth 또는 JWT에서 얻은 API 인증 header를 준비한 뒤 다음 값을 채우고 실행합니다.

```bash
PROVIDER_E2E_SMOKE_ENABLED=true \
PROVIDER_E2E_DRY_RUN=false \
BACKEND_BASE_URL=http://localhost:8080 \
PROVIDER_E2E_AUTH_MODE=oauth-header \
PROVIDER_E2E_AUTH_HEADER='Authorization: Bearer oauth_or_jwt_token' \
PROVIDER_E2E_REPO_FULL_NAME=owner/repo \
PROVIDER_E2E_ISSUE_NUMBER=123 \
PROVIDER_E2E_BASE_BRANCH=main \
PROVIDER_E2E_HEAD_BRANCH=provider-e2e-smoke \
EMBEDDING_BACKFILL_OPERATION_TOKEN=operation_token \
bash tools/smoke/run-provider-e2e-smoke.sh
```

실행 전 env만 확인할 때는 `bash tools/smoke/validate-provider-e2e-env.sh`, 실제 호출 없이 순서만 확인할 때는 `PROVIDER_E2E_DRY_RUN=true bash tools/smoke/run-provider-e2e-smoke.sh`를 사용합니다.

AI provider 직접 smoke는 opt-in입니다. `AI_PROVIDER_SMOKE_MODE=dry-run`은 네트워크 호출 없이 OpenAI-compatible chat/embedding, Vertex text/embedding, timeout/error observation, provider fallback 확인 순서를 출력합니다. `AI_PROVIDER_SMOKE_MODE=live`와 실제 key를 설정하면 OpenAI-compatible chat completion과 embedding endpoint를 직접 호출합니다.

```bash
AI_PROVIDER_SMOKE_ENABLED=true \
AI_PROVIDER_SMOKE_MODE=dry-run \
AI_PROVIDER_SMOKE_TIMEOUT_SECONDS=30 \
AI_PROVIDER=openai-compatible \
AI_API_BASE_URL=https://api.openai.com/v1 \
AI_API_KEY=openai_or_compatible_api_key \
AI_CHAT_MODEL=gpt-4o-mini \
AI_EMBEDDING_MODEL=text-embedding-3-small \
AI_EMBEDDING_DIMENSIONS=1536 \
bash tools/smoke/validate-ai-provider-smoke-env.sh

AI_PROVIDER_SMOKE_ENABLED=true \
AI_PROVIDER_SMOKE_MODE=dry-run \
AI_PROVIDER_SMOKE_TIMEOUT_SECONDS=30 \
AI_PROVIDER=openai-compatible \
AI_API_BASE_URL=https://api.openai.com/v1 \
AI_API_KEY=openai_or_compatible_api_key \
AI_CHAT_MODEL=gpt-4o-mini \
AI_EMBEDDING_MODEL=text-embedding-3-small \
AI_EMBEDDING_DIMENSIONS=1536 \
bash tools/smoke/run-ai-provider-smoke.sh
```

## OCI Always Free Terraform

OCI Always Free A1 Flex 배포 baseline은 `infra/terraform/oci/always-free-a1-flex`에 둡니다.

- 기본 shape: `VM.Standard.A1.Flex`
- 기본 resource prefix: `aquila-buddy-a1`
- 기본 hostname label: `aquilabuddy`
- 기본 VCN DNS label: `aquilabuddyvcn`
- storage: boot volume `50GB`, data Block Volume `150GB`, data volume `0` VPU/GB
- data mount: `/var/lib/aquila-data`, Docker data-root `/var/lib/aquila-data/docker`

실제 OCID, fingerprint, private key, SSH key는 commit하지 않습니다. `terraform.tfvars.example` 또는 `terraform.env.example`를 복사해 로컬에서만 실제 값으로 채웁니다.

```bash
cd infra/terraform/oci/always-free-a1-flex
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform fmt -check
terraform validate
terraform plan
```

환경 변수 방식으로 실행할 때는 shell export template을 사용합니다.

```bash
cd infra/terraform/oci/always-free-a1-flex
cp terraform.env.example terraform.env
# terraform.env에 실제 OCID, fingerprint, SSH public key를 채웁니다.
set -a
source terraform.env
set +a
terraform init
terraform plan
```

`terraform.tfvars`, `terraform.env`, `.terraform/`, `*.tfstate`, `*.tfplan`은 gitignore 대상입니다.

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

Extension live E2E는 headless Chrome DevTools endpoint를 사용합니다. CI runner에서 Chrome startup이 느릴 수 있으므로 다음 env로 대기 시간과 stderr logging을 고정합니다.

```bash
EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS=30000 \
EXTENSION_E2E_CHROME_EXTRA_FLAGS="--enable-logging=stderr --v=1" \
npm --prefix extension run test:e2e
```

`Chrome DevTools endpoint did not open`이 발생하면 로그의 `exitCode`, `signal`, `stderr`를 먼저 확인합니다. 로컬 sandbox에서 loopback listen이 막히면 승인된 환경에서 같은 명령을 재실행해야 합니다.

## Agent Docs

Machine-facing agent settings are local-only and ignored by git:

- `AGENTS.md`
- `docs/AGENT-CONTEXT.md`
- `docs/agent/*.md`
- `.codex/tasks/*.local`
