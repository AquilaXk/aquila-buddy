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
