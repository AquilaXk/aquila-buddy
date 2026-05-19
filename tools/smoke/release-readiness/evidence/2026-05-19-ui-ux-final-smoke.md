# Frontend UI UX Final Smoke - 2026-05-19 main 86359d9

## 대상
- issue: `#313`
- commit: `86359d9d27415aff2d1371d6088d9fe7c99906b9`
- branch: `main`
- recorded_at: `2026-05-19T09:55:00+09:00`
- scope: 디자인을 제외한 UI UX 구조, route, CTA, 상태 전환 검증

## 실측 결과
| 영역 | 결과 | 검증 | 비고 |
| --- | --- | --- | --- |
| route contract | pass | `npm --prefix front run test:e2e` | 8개 route 순서와 validator 연결 고정 |
| state coverage | pass | `npm --prefix front run test:e2e` | normal, empty, loading, error, result 상태 포함 |
| browser integration smoke | pass | `npm --prefix front run smoke:integration` | fixture backend + local Next + Chrome session |
| protected redirect | pass | `npm --prefix front run smoke:integration` | `/dashboard` 미인증 진입이 `/login?next=`로 이동 |
| login to dashboard | pass | `npm --prefix front run smoke:integration` | fixture token 저장 후 dashboard 진입 |
| profile flow | pass | `npm --prefix front run smoke:integration` | profile language/vector 관련 화면 문구 확인 |
| issue list/detail flow | pass | `npm --prefix front run smoke:integration` | issue list, issue detail, PR 초안 진입 확인 |
| PR draft/detail flow | pass | `npm --prefix front run smoke:integration` | PR draft 생성 화면과 `close #261` 상세 확인 |
| frontend build | pass | `npm --prefix front run build` | Next production build 통과 |
| frontend typecheck | pass | `npm --prefix front run typecheck` | build 후 생성된 `.next/types` 기준 통과 |

## 판정
- 로그인, OAuth callback, 대시보드, 프로필, 이슈 목록, 이슈 상세, PR 초안, PR 상세 route의 UI UX 구조 계약은 통과했습니다.
- 색상, 폰트, 장식 이미지, 브랜드 스킨 차이는 이 검증 범위에서 제외했습니다.
- fixture backend 기반 브라우저 통합 smoke에서 주요 사용자 동선이 통과했습니다.

## 후속
- 실제 staging backend, OAuth session, live provider가 필요한 운영 smoke는 별도 live issue에서 계속 추적합니다.
