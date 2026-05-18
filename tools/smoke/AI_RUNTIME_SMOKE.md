# AI 운영 smoke runbook

이 문서는 운영 배포 전에 `AI_PROVIDER=openai-compatible`, GitHub API token, pgvector 추천 경로가 함께 동작할 수 있는지 확인하는 최소 절차를 정리합니다.

## 점검 범위

- issue guide, PR 초안, 번역은 chat completion provider를 사용합니다.
- profile embedding, issue embedding, pgvector ranking은 embedding provider와 PostgreSQL `vector(1536)` schema를 사용합니다.
- GitHub repository tree, contents, compare, pull request sample 조회는 `GITHUB_TOKEN`으로 호출합니다.
- Langfuse는 선택 기능이며, 켜져 있을 때 AI 호출의 prompt version, model, latency, token usage, 실패 사유를 trace로 남깁니다.

## AI provider env

OpenAI-compatible 운영 smoke에는 다음 env가 필요합니다.

```bash
AI_PROVIDER=openai-compatible
AI_API_BASE_URL=https://api.openai.com/v1
AI_API_KEY=openai_or_compatible_api_key
AI_CHAT_MODEL=gpt-4o-mini
AI_TEMPERATURE=0.2
AI_EMBEDDING_MODEL=text-embedding-3-small
AI_EMBEDDING_DIMENSIONS=1536
AI_REQUEST_TIMEOUT_MILLIS=30000
```

`AI_API_KEY` 대신 `OPENAI_API_KEY`를 사용할 수 있습니다. `AI_EMBEDDING_DIMENSIONS`는 pgvector schema의 `vector(1536)`과 맞춰야 합니다. `AI_REQUEST_TIMEOUT_MILLIS`는 provider timeout/error observation과 fallback 동작을 재현할 때도 같은 기본값을 사용합니다.

## GitHub token 권한

운영 smoke에서 사용하는 token은 repository 읽기 권한만 필요합니다. Fine-grained personal access token을 쓸 때는 대상 repository를 제한하고 다음 권한을 부여합니다.

| 권한 | 수준 | 사용 경로 |
| --- | --- | --- |
| Metadata | read | 사용자 repository 목록, 기본 repository metadata |
| Contents | read | contents API, git tree fallback, compare diff |
| Pull requests | read | merged PR sample, PR 초안 참고 context |
| Issues | read | issue search/crawl을 운영에서 켤 때 |

Classic token을 쓸 때는 공개 repository smoke만 확인하면 `public_repo`, private repository까지 확인하면 `repo` scope를 사용합니다. OAuth 로그인 smoke에는 OAuth app의 `read:user`, `user:email` scope가 필요합니다.

Smoke script에는 사람이 확인한 권한 문자열을 함께 넘깁니다.

```bash
GITHUB_TOKEN=github_pat_or_fine_grained_token
GITHUB_TOKEN_PERMISSIONS=metadata:read,contents:read,pull-requests:read,issues:read
```

실제 API 응답에서 권한이 부족하면 GitHub REST 응답의 `X-Accepted-GitHub-Permissions` header를 확인해 token 권한을 조정합니다.

## pgvector env와 init

pgvector ranking smoke에는 PostgreSQL 연결 env와 similarity provider 설정이 필요합니다.

```bash
RECOMMENDATION_SIMILARITY_PROVIDER=pgvector
RECOMMENDATION_SIMILARITY_LIMIT=20
DB_URL=jdbc:postgresql://localhost:5432/aquila_buddy
DB_USER=aquila
DB_PASSWORD=aquila
```

로컬 또는 운영 초기화 전에는 pgvector init script를 dry-run으로 먼저 확인합니다.

```bash
bash tools/test/run-pgvector-integration.sh --validate-only
bash tools/test/run-pgvector-integration.sh --compose --dry-run
bash tools/db/init-postgres-pgvector.sh --dry-run
bash tools/test/run-pgvector-integration.sh
```

운영 DB에는 `schema.sql` 적용 후 `schema-postgres-pgvector.sql`을 적용해 `profile_embedding`과 `embedding`이 `vector(1536)`으로 변환되어야 합니다.
`schema-postgres-pgvector.sql`은 `idx_user_profiles_profile_embedding`, `idx_recommended_issues_issue_embedding` HNSW index를 생성해야 하며, 실제 Postgres 통합 테스트는 column type과 index definition을 함께 확인합니다.
embedding backfill 후 추천 순위가 score fallback 순서에서 pgvector similarity 순서로 바뀌는지도 `PgvectorRecommendedIssueSimilarityIntegrationTest`에서 검증합니다.
로컬 격리 검증은 `bash tools/test/run-pgvector-integration.sh --compose`로 실행하며, 기본 포트 `54329`는 `PGVECTOR_TEST_DB_PORT`로 변경할 수 있습니다.
`/api/v1/embeddings/backfill`은 `APP_EMBEDDING_BACKFILL_OPERATION_TOKEN`이 설정된 backend에서만 열고, 호출 시 `X-Aquila-Operation-Token`과 운영자 식별용 `X-Aquila-Operator` header를 함께 남깁니다.
`EMBEDDING_BACKFILL_MAX_LIMIT`은 요청 limit 상한, `EMBEDDING_BACKFILL_MIN_INTERVAL_SECONDS`는 연속 실행 최소 간격입니다.

## smoke 순서

1. PostgreSQL/pgvector를 준비합니다.
2. `bash tools/smoke/validate-ai-runtime-env.sh`로 env와 token 권한 문자열을 검증합니다.
3. `bash tools/smoke/validate-ai-provider-smoke-env.sh`와 `bash tools/smoke/run-ai-provider-smoke.sh`로 AI provider 직접 호출 smoke를 검증합니다.
4. `bash tools/smoke/validate-langfuse-observability-env.sh`와 `bash tools/smoke/run-langfuse-observability-smoke.sh`로 Langfuse trace 전송 경로를 opt-in으로 검증합니다.
5. `bash tools/smoke/validate-provider-e2e-env.sh`로 실제 provider E2E env를 검증합니다.
6. backend를 운영 profile과 동일한 env로 실행합니다.
7. OAuth 로그인으로 API 인증 header를 준비합니다.
8. `bash tools/smoke/run-provider-e2e-smoke.sh`로 profile vector, issue crawl, pgvector recommendation, issue guide, PR draft, translation을 순서대로 실행합니다.

스크립트 없이 수동으로 점검할 때의 상세 흐름은 다음과 같습니다.

1. profile vector refresh를 실행하고 `profile_embedding` 저장을 확인합니다.
2. issue crawl/search를 실행하고 `issue_embedding` 저장을 확인합니다.
3. `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector` 상태에서 추천 순위가 embedding similarity를 반영하는지 확인합니다.
4. issue guide, PR draft, translation을 실행하고 `ai_call_observations`와 Langfuse trace를 확인합니다.

OpenAI-compatible + pgvector env 검증 예시:

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

실제 provider E2E smoke env와 실행 예시:

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
bash tools/smoke/validate-provider-e2e-env.sh

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

backend에는 같은 token 값을 `APP_EMBEDDING_BACKFILL_OPERATION_TOKEN`으로 주입해야 `/api/v1/embeddings/backfill`이 live smoke에서 열립니다. dry-run으로 순서만 확인하려면 `PROVIDER_E2E_DRY_RUN=true`를 사용합니다.

AI provider 직접 smoke env와 실행 예시:

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

dry-run은 OpenAI-compatible chat completion, OpenAI-compatible embedding, Vertex AI text generation, Vertex AI embedding, provider timeout/error observation, provider fallback path 순서를 출력합니다. live mode는 OpenAI-compatible endpoint를 직접 호출하며 Vertex AI live smoke는 credential이 주입된 backend/provider E2E smoke에서 확인합니다.

Langfuse observability smoke env와 실행 예시:

```bash
LANGFUSE_SMOKE_ENABLED=true \
LANGFUSE_SMOKE_MODE=dry-run \
LANGFUSE_SMOKE_TIMEOUT_SECONDS=30 \
LANGFUSE_BASE_URL=https://cloud.langfuse.com \
LANGFUSE_PUBLIC_KEY=pk_langfuse \
LANGFUSE_SECRET_KEY=sk_langfuse \
LANGFUSE_ENVIRONMENT=staging \
bash tools/smoke/validate-langfuse-observability-env.sh

bash tools/smoke/run-langfuse-observability-smoke.sh --dry-run

LANGFUSE_SMOKE_ENABLED=true \
LANGFUSE_SMOKE_MODE=live \
LANGFUSE_SMOKE_TIMEOUT_SECONDS=30 \
LANGFUSE_BASE_URL=https://cloud.langfuse.com \
LANGFUSE_PUBLIC_KEY=pk_langfuse \
LANGFUSE_SECRET_KEY=sk_langfuse \
LANGFUSE_ENVIRONMENT=staging \
bash tools/smoke/run-langfuse-observability-smoke.sh
```

dry-run은 성공 generation payload와 실패 generation payload가 prompt version, model, latency, usage, error type, failure reason metadata를 포함하는지 확인할 항목을 출력합니다. live mode는 synthetic trace event만 전송하며 실제 issue guide/PR draft/translation 호출과 연결된 trace는 provider E2E smoke 이후 Langfuse 화면에서 같은 필드가 남는지 확인합니다.

## 실패 시 확인 순서

- provider 401/403: `AI_API_KEY` 또는 `OPENAI_API_KEY` 주입 여부를 확인합니다.
- embedding 차원 오류: provider 설정의 embedding dimension과 pgvector schema의 `1536` 차원을 맞춥니다.
- GitHub 403: `GITHUB_TOKEN_PERMISSIONS`와 실제 token 권한을 비교하고 `X-Accepted-GitHub-Permissions` header를 확인합니다.
- pgvector query 오류: `schema-postgres-pgvector.sql` 적용 여부와 extension 생성 권한을 확인합니다.
- backfill 후 추천 순위가 바뀌지 않음: `issue_embedding IS NOT NULL` 대상 수, `idx_recommended_issues_issue_embedding` index definition, `RECOMMENDATION_SIMILARITY_PROVIDER=pgvector` 적용 여부를 확인합니다.
- Langfuse 누락: `LANGFUSE_ENABLED`, `LANGFUSE_SMOKE_ENABLED`, base URL, public/secret key, outbound network를 확인합니다.

## rollback

- AI provider 문제는 `AI_PROVIDER=vertex-ai`로 되돌리거나 fixture profile에서 smoke를 재시도합니다.
- pgvector 문제는 `RECOMMENDATION_SIMILARITY_PROVIDER=score`로 되돌려 추천 기본 ranking을 유지합니다.
- Langfuse 문제는 `LANGFUSE_ENABLED=false`로 비활성화합니다.
