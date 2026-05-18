# Release Readiness Record - YYYY-MM-DD main <short-sha>

## 대상
- commit: `<full-sha>`
- branch: `main`
- ci_run: `<github-actions-run-id>`
- recorded_at: `<ISO-8601>`
- recorder: `<name-or-role>`

## smoke 결과
| 영역 | 결과 | 증거 | 비고 |
| --- | --- | --- | --- |
| backend | pending-manual | `<run-or-command>` | `<note>` |
| frontend | pending-manual | `<run-or-command>` | `<note>` |
| extension | pending-manual | `<run-or-command>` | `<note>` |
| pgvector | pending-manual | `<run-or-command>` | `<note>` |
| operations | pending-manual | `<run-or-command>` | `<note>` |
| ai-provider | pending-manual | `<run-or-command>` | `<note>` |
| langfuse | pending-manual | `<run-or-command>` | `<note>` |
| provider-e2e | pending-manual | `<run-or-command>` | `<note>` |

## secret 점검
- secret_values_recorded: `false`
- required_for_live_smoke: `<secret-names-only>`
- missing_for_live_smoke: `<secret-names-only-or-none>`
- storage_policy: `환경 변수 또는 GitHub Actions secret으로만 주입`

## rollback 기준
- AI provider 오류: `<flag-or-action>`
- pgvector 오류: `<flag-or-action>`
- Langfuse 오류: `<flag-or-action>`
- frontend smoke 오류: `<flag-or-action>`
- backfill 오류: `<flag-or-action>`

## 재실행 체크리스트
- [ ] backfill 실행 후 pgvector 추천 smoke 재실행
- [ ] Langfuse live secret 주입 후 observability smoke 재실행
- [ ] provider E2E OAuth/JWT 준비 후 E2E smoke 재실행
- [ ] 실패한 smoke가 있으면 rollback 기준 적용 여부 기록
