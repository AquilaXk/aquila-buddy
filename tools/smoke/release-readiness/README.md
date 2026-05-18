# Release readiness records

배포 후보별 운영 점검 결과는 이 디렉터리에 commit 단위 markdown으로 남깁니다.

## 기록 원칙

- secret 값, token 원문, OAuth/JWT bearer 값은 기록하지 않습니다.
- `main` commit, CI run, smoke 결과, secret 누락 여부, rollback 기준을 함께 남깁니다.
- live provider, Langfuse, provider E2E smoke는 opt-in이므로 실행하지 못한 경우 `pending-manual`과 필요한 secret 종류만 기록합니다.
- backfill, Langfuse live 전송, provider E2E 재실행이 필요한 경우 `재실행 체크리스트`에 남깁니다.

## 파일명

```text
YYYY-MM-DD-main-<short-sha>.md
```

예: `2026-05-18-main-04fb270.md`

## 작성 순서

1. `TEMPLATE.md`를 새 record 파일로 복사합니다.
2. 대상 commit, CI run, 실행 시각을 채웁니다.
3. CI에서 자동으로 확인한 backend, frontend, extension, pgvector, operations 결과를 기록합니다.
4. live smoke를 실행했다면 결과와 증거 링크만 남깁니다.
5. live smoke를 실행하지 못했다면 secret 값 없이 필요한 secret 종류와 재실행 조건을 기록합니다.
6. `bash tools/smoke/validate-release-readiness-record.sh`로 형식을 확인합니다.
