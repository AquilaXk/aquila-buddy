# 동등성 완료 매트릭스

이 문서는 화면, API, 확장 프로그램, 운영 smoke의 완료 상태를 한 곳에서 확인하기 위한 기준표입니다.

## 상태 기준
| 상태 | 의미 |
| --- | --- |
| 완료 | 구현과 검증 증적이 모두 존재합니다. |
| 자동 검증 | CI 또는 로컬 명령으로 반복 검증됩니다. |
| 수동 검증 | secret, OAuth session, staging endpoint처럼 수동 준비가 필요한 검증입니다. |
| 미완료 | 구현 또는 검증 증적이 아직 없습니다. |

## 화면 UI UX
| 영역 | 상태 | 검증 | 확인 범위 |
| --- | --- | --- | --- |
| 로그인 | 자동 검증 | `tools/frontend/validate-login-parity.mjs` | GitHub OAuth 진입, brand shell, CTA 구조 |
| OAuth callback | 자동 검증 | `tools/frontend/validate-oauth-callback-parity.mjs`, `back/src/test/java/com/aquilabuddy/global/auth/oauth/OAuthCallbackControllerTest.java` | token handoff, callback state, error display |
| 대시보드 | 자동 검증 | `tools/frontend/validate-dashboard-parity.mjs`, `tools/frontend/validate-ui-ux-flow-parity.mjs` | 추천/활동/workflow overview, 핵심 동선 |
| 프로필 | 자동 검증 | `tools/frontend/validate-profile-parity.mjs`, `back/src/test/java/com/aquilabuddy/adapter/in/web/user/UserProfileControllerTest.java` | profile edit, vector refresh, public profile field |
| 이슈 목록 | 자동 검증 | `tools/frontend/validate-issues-parity.mjs`, `back/src/test/java/com/aquilabuddy/domain/issue/IssueRecommendationControllerTest.java` | crawl/search, recommend list, history, score display |
| 이슈 상세 | 자동 검증 | `tools/frontend/validate-issue-detail-parity.mjs`, `back/src/test/java/com/aquilabuddy/domain/analysis/IssueGuideControllerTest.java` | guide, pseudo code, workflow, file paths |
| PR 초안 | 자동 검증 | `tools/frontend/validate-pr-draft-parity.mjs`, `back/src/test/java/com/aquilabuddy/adapter/in/web/prdraft/PullRequestDraftControllerTest.java` | source issue prefill, diff, repository guide, draft 생성 |
| PR 상세 | 자동 검증 | `tools/frontend/validate-pr-detail-parity.mjs`, `tools/frontend/validate-ui-ux-flow-parity.mjs` | draft detail, diff, edit/delete, submission bundle, translation timeline |
| shell/brand theme | 자동 검증 | `tools/frontend/validate-shell-parity.mjs`, `tools/frontend/validate-brand-gothic-parity.mjs` | aquila-buddy shell, aquila-bank/aquila-blog asset family, favicon/대표 이미지 |

## API 계약
| 영역 | 상태 | 검증 | 확인 범위 |
| --- | --- | --- | --- |
| 공통 응답 envelope | 자동 검증 | `tools/frontend/validate-api-envelope-parity.mjs`, `back/src/test/java/com/aquilabuddy/global/response/ApiResponseTest.java` | success/data/error 구조 |
| health | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/in/web/health/HealthControllerTest.java` | service health response |
| 사용자 profile | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/in/web/user/UserProfileControllerTest.java` | me/public profile, id 호환 field, vector refresh |
| OAuth/JWT | 자동 검증 | `back/src/test/java/com/aquilabuddy/global/auth/oauth/OAuthCallbackControllerTest.java` | callback token, 실패 redirect, principal |
| issue crawl/recommend/lookup | 자동 검증 | `back/src/test/java/com/aquilabuddy/domain/issue/IssueRecommendationControllerTest.java` | GitHub issue search, recommendation, lookup |
| issue guide/pseudo/workflow | 자동 검증 | `back/src/test/java/com/aquilabuddy/domain/analysis/IssueGuideControllerTest.java`, `back/src/test/java/com/aquilabuddy/application/service/analysis/IssueGuideServiceTest.java` | AI guide, pseudo code, workflow, cache/limit |
| repository code context | 자동 검증 | `back/src/test/java/com/aquilabuddy/domain/analysis/RepositoryCodeContextTest.java` | file selection, truncation, fallback |
| PR draft | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/in/web/prdraft/PullRequestDraftControllerTest.java`, `back/src/test/java/com/aquilabuddy/adapter/out/ai/AiGeneratedOutputQualityRegressionTest.java` | close line, markdown, context, parser fallback |
| PR translation | 자동 검증 | `back/src/test/java/com/aquilabuddy/application/service/prdraft/PullRequestDraftTranslationServiceTest.java`, `back/src/test/java/com/aquilabuddy/adapter/out/fixture/prdraft/RuleBasedPullRequestDraftTranslationAdapterTest.java` | English title/body, timeline/activity/submission |
| embedding backfill | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/in/web/embedding/EmbeddingBackfillControllerTest.java` | operation token, limit guard, audit result |
| AI observability | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/in/web/ai/AiCallObservabilityControllerTest.java`, `back/src/test/java/com/aquilabuddy/domain/ai/AiCallObservationTest.java` | prompt version, model, latency, token usage, failure reason |
| pgvector recommendation | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/out/persistence/issue/PgvectorRecommendedIssueSimilarityIntegrationTest.java` | vector type, HNSW index, similarity ranking |

## 확장 프로그램
| 영역 | 상태 | 검증 | 확인 범위 |
| --- | --- | --- | --- |
| manifest/popup/content script | 자동 검증 | `npm --prefix extension run validate` | manifest v3, popup wiring, JS syntax |
| issue list badge | 자동 검증 | `npm --prefix extension run test:e2e` | 추천 badge 삽입, 중복 방지, unrelated issue 제외 |
| issue detail sidebar | 자동 검증 | `npm --prefix extension run test:e2e` | sidebar toggle, guide/pseudo loading, error state |
| PR autofill | 자동 검증 | `npm --prefix extension run test:e2e` | issue number 입력, draft 생성, translation, title/body 적용 |
| GitHub Turbo navigation | 자동 검증 | `npm --prefix extension run test:e2e` | sidebar/toggle 재초기화 |
| XSS escaping | 자동 검증 | `tools/extension/validate-analysis-filepaths.mjs`, `npm --prefix extension run test:e2e` | file path, recommendation reason, PR draft preview escaping |

## 운영 smoke 체크리스트
| 영역 | 상태 | 검증 | 확인 범위 |
| --- | --- | --- | --- |
| AI provider 설정 | 자동 검증 | `tools/smoke/validate-ai-runtime-env.sh`, `tools/smoke/validate-ai-provider-smoke-env.sh` | `AI_PROVIDER=openai-compatible`, chat model, embedding model, dimension |
| AI provider live 호출 | 수동 검증 | `tools/smoke/run-ai-provider-smoke.sh` | chat completion, embedding, timeout/error path |
| OAuth/JWT 운영 인증 | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | OAuth 또는 JWT 인증 header로 user context 확인 |
| profile vector | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | `/api/v1/users/me/vector` profile embedding 저장 |
| issue crawl | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | GitHub token scope, search query, collected issue 저장 |
| embedding backfill | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/in/web/embedding/EmbeddingBackfillControllerTest.java` | admin token, limit guard, audit log |
| embedding backfill live | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | staging DB에서 backfill 전후 결과 기록 |
| pgvector init | 자동 검증 | `back/src/test/java/com/aquilabuddy/adapter/out/persistence/issue/PgvectorInitContractTest.java`, `tools/smoke/release-readiness/2026-05-19-main-3f2f1d5.md` | schema 적용, vector type, HNSW index |
| pgvector 추천 live | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | vector index 기반 recommend/lookup, ranking 변화 |
| issue guide live | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | provider 응답 기반 guide 생성, fallback 없는 성공 |
| PR draft live | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | diff, issue context, analysis context 기반 draft 생성 |
| translation live | 수동 검증 | `tools/smoke/run-provider-e2e-smoke.sh` | 생성 draft id의 영문 title/body 생성 |
| Langfuse 관측 | 자동 검증 | `tools/smoke/validate-langfuse-observability-env.sh`, `back/src/test/java/com/aquilabuddy/adapter/out/persistence/ai/PersistentAiCallObserverTest.java` | prompt version, model, latency, token usage, failure reason 저장 계약 |
| Langfuse live trace | 수동 검증 | `tools/smoke/run-langfuse-observability-smoke.sh` | live endpoint trace 적재 확인 |
| secret hygiene | 자동 검증 | `tools/smoke/validate-release-readiness-record.sh` | record에 token/API key 원문이 없는지 검사 |
| release readiness record | 자동 검증 | `tools/smoke/release-readiness/2026-05-19-main-3f2f1d5.md` | main commit, CI run, pending-manual live smoke 조건 |

## 필수 검증 명령
| 목적 | 명령 |
| --- | --- |
| backend 전체 검증 | `./back/gradlew -p back check` |
| frontend type/build/smoke | `npm --prefix front run typecheck`, `npm --prefix front run build`, `npm --prefix front run smoke:integration` |
| 확장 검증 | `npm --prefix extension run validate`, `npm --prefix extension run test:e2e` |
| release readiness record | `bash tools/smoke/validate-release-readiness-record.sh` |
| provider E2E disabled skip | `PROVIDER_E2E_SMOKE_ENABLED=false bash tools/smoke/run-provider-e2e-smoke.sh` |
| provider E2E dry-run | `PROVIDER_E2E_SMOKE_ENABLED=true PROVIDER_E2E_DRY_RUN=true ... bash tools/smoke/run-provider-e2e-smoke.sh` |
| provider E2E live | `PROVIDER_E2E_SMOKE_ENABLED=true PROVIDER_E2E_DRY_RUN=false ... bash tools/smoke/run-provider-e2e-smoke.sh` |

## 판정
- 프론트엔드 구조와 핵심 UI UX 흐름은 자동 검증 상태입니다.
- 백엔드 API 계약과 AI 생성물 품질 게이트는 자동 검증 상태입니다.
- 확장 프로그램의 GitHub 유사 DOM 흐름은 자동 검증 상태입니다.
- 실제 staging secret, OAuth session, Langfuse endpoint, staging DB가 필요한 운영 smoke는 수동 검증 상태입니다.
- release readiness record는 secret 값을 기록하지 않고 `pending-manual` 재실행 조건을 남깁니다.
