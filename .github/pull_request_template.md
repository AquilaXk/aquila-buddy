> 제목은 `[Type] 한국어 작업 요약` 형식으로 작성합니다. (`Type`: `Feat`, `Fix`, `Refactor`, `Style`, `Docs`, `Test`, `Chore`, `Build`, `CI`, `Perf`, `Revert`, `Hotfix`)
> 본문은 한국어 중심으로 작성하고 API, OAuth, SQL, CI처럼 현업 공통 용어만 영어를 유지합니다.

## 관련 이슈 및 거버넌스 연결 (Epic → Sub-Issue → Vertical-Slice PR)
- Closes #
- Parent Epic: #

## 변경 등급 (Change Classification)
- [ ] **Class A** (Architecture / Major Feature / Cross-Layer Overhaul — Epic 산하 Sub-Issue 및 Vertical-Slice PR)
- [ ] **Class B** (Standard Bugfix / Scoped Enhancement — 원인 수정 + 회귀 테스트 + 문서 동기화 단일 PR)
- [ ] **Class C** (Trivial Maintenance / Config / Chore — 연관 유지보수 작업군 배치 PR)

## 요약
- 이 PR이 어떤 문제를 해결하고 어떤 결과를 남기는지 2~3줄로 설명해 주세요.

## 주요 변경 사항
- 변경된 아키텍처, API 계약, UI/UX 또는 운영 설정을 구체적으로 적어 주세요.

## 영향 범위
- [ ] 프론트엔드 (`front/`)
- [ ] 백엔드 (`back/`)
- [ ] Chrome 확장 프로그램 (`extension/`)
- [ ] AI / GitHub 연동 / pgvector (`tools/db/`, `tools/smoke/`)
- [ ] 인프라 / CI / 거버넌스 (`.github/`, `deploy/`, `infra/`)
- [ ] 문서 (`README.md`, `docs/`)

## 결정론적 검증 결과 (Deterministic Verification)
- [ ] 변경 영역의 자동화 테스트 및 정적 검증을 모두 통과했는가?
- [ ] 회귀 방지 테스트 또는 계약 검증을 확인했는가?

### 실행한 검증 명령 및 결과
- 실제로 실행한 검증 명령과 통과 여부를 적어 주세요.
- 예) `./back/gradlew -p back check`
- 예) `npm --prefix front run typecheck && npm --prefix front run build`
- 예) `node tools/extension/validate-extension.mjs`
- 예) `node tools/db/validate-pgvector-init.mjs`

## 화면 / API 예시
- UI 변경이나 응답 계약 변경이 있으면 확인에 필요한 증거를 첨부해 주세요.

## 리스크와 롤백
- 예상 리스크:
- 롤백 방법:

## 체크리스트
- [ ] 하위 이슈(`Closes #`)와 상위 에픽(`Parent Epic: #`)을 정확히 연결했는가?
- [ ] base branch가 `main`인지 확인했는가?
- [ ] PR 범위 밖 무관한 변경을 제외했는가?
- [ ] 결정론적 검증 명령 실행 결과를 본문에 기록했는가?
- [ ] API 계약 또는 운영 문서 변경이 있다면 함께 반영했는가?
