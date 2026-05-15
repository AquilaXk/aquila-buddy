# 웹 핵심 기능 PR 로드맵 설계

## 목적

웹 애플리케이션에서 사용자가 로그인 이후 프로필을 확인하고, 추천 이슈를 탐색하고, 이슈 해결 가이드를 확인한 뒤, PR 초안 작성과 번역까지 이어지는 핵심 흐름을 PR 단위로 구현한다.

첫 단계에서는 실제 외부 API, AI 모델, DB persistence를 완성하려고 하지 않는다. 각 PR은 Java 21 + Spring Boot API 계약과 Next.js 화면 연결을 함께 제공해 로컬에서 동작을 확인할 수 있어야 한다.

## 범위

포함한다.
- 사용자 프로필 조회 화면과 API 계약
- 추천 이슈 목록, 이력, 상세 가이드, 의사 코드 화면과 API 계약
- PR 초안 생성, 상세 조회, 수정, 번역 화면과 API 계약
- 각 기능의 backend controller/service 테스트와 frontend typecheck/build 검증

제외한다.
- Chrome extension
- 실제 OAuth 로그인 완성
- 실제 GitHub API 크롤링
- 실제 AI 모델 호출
- PostgreSQL/pgvector persistence 완성
- Langfuse 모니터링 연동

## 구현 원칙

각 PR은 사용자가 화면에서 확인할 수 있는 기능 단위로 나눈다. 백엔드는 `ApiResponse<T>` 응답 형식을 유지하고, 프론트는 `front/src/lib/api.ts`에 타입과 호출 함수를 먼저 정의한 뒤 화면에서 사용한다.

도메인 모델은 `back/src/main/java/com/aquilabuddy/domain`, use case 성격의 서비스는 `application`, HTTP controller는 `global` 아래에 둔다. Kotlin source는 만들지 않는다.

초기 API는 데모 데이터를 반환해도 된다. 단, 응답 구조는 이후 실제 연동으로 교체해도 화면 계약이 크게 바뀌지 않도록 유지한다.

## PR 순서

1. `[Feat] 사용자 프로필 API와 프로필 화면 연결`
   - `GET /api/v1/users/me`
   - `/profile`에서 사용자 이름, GitHub ID, 주요 언어, 관심 토픽, 프로필 준비 상태를 표시한다.
   - 완료 조건: 백엔드 테스트와 프론트 빌드가 통과하고, 프로필 화면이 API 데이터를 렌더링한다.

2. `[Feat] 추천 이슈 이력 API와 화면 연결`
   - `GET /api/v1/issues/recommend/history`
   - `/issues` 화면에 현재 추천 목록과 별도로 최근 추천 이력을 표시한다.
   - 완료 조건: 추천 이력 응답 타입과 화면 렌더링이 연결된다.

3. `[Feat] 이슈 의사코드 API와 상세 화면 확장`
   - `GET /api/v1/issues/{issueId}/pseudo`
   - `/issues/[id]`에서 의사 코드 데이터를 독립 API로 조회해 표시한다.
   - 완료 조건: 기존 가이드 API와 의사 코드 API가 상세 화면에서 함께 동작한다.

4. `[Feat] PR 초안 생성 API와 작성 화면 연결`
   - `POST /api/v1/pr`
   - `/pr-draft`에서 diff, 이슈 요약, 작업 유형을 입력하면 제목과 본문 초안을 생성한다.
   - 완료 조건: 입력 폼, 성공 결과, 실패 상태가 화면에 표시된다.

5. `[Feat] PR 초안 상세/수정 API와 상세 화면 연결`
   - `GET /api/v1/pr/{id}`
   - `PATCH /api/v1/pr/{id}`
   - `/pr/[id]`에서 초안 상세를 보고 제목/본문을 수정한다.
   - 완료 조건: 상세 조회와 수정 저장 흐름이 한 화면에서 검증된다.

6. `[Feat] PR 초안 번역 API와 화면 연결`
   - `POST /api/v1/pr/{id}/translate`
   - `/pr/[id]`에서 한국어 초안을 영어 초안으로 변환한 결과를 표시한다.
   - 완료 조건: 번역 결과 타입, 화면 상태, 실패 상태가 검증된다.

7. `[Build] 로컬 인프라 기본 구성`
   - Docker Compose 기반 로컬 DB 실행 구성을 추가한다.
   - 실제 persistence를 붙이기 전에 개발자가 동일한 실행 기반을 사용할 수 있게 한다.
   - 완료 조건: 문서화된 명령으로 로컬 인프라를 띄울 수 있고, 기존 backend/frontend 검증이 유지된다.

## 데이터 흐름

초기 웹 핵심 흐름은 다음 계약을 따른다.

1. 프론트 서버 컴포넌트 또는 화면 로더가 `front/src/lib/api.ts`의 API 함수를 호출한다.
2. 백엔드 controller는 `ApiResponse<T>`로 응답한다.
3. application service는 데모 데이터를 반환한다.
4. 화면은 성공, 빈 상태, 실패 상태를 모두 렌더링한다.
5. 이후 실제 외부 연동은 service 내부 구현을 교체하는 별도 PR에서 처리한다.

## 에러 처리

초기 단계에서는 API 호출 실패 시 화면 상단에 기능별 에러 배너를 표시한다. 백엔드는 예상 가능한 실패를 추가하기 전까지 200 성공 응답과 일반 예외 처리만 유지한다.

실제 외부 API, AI, DB가 붙는 단계에서 도메인별 예외와 에러 코드를 분리한다.

## 테스트 전략

각 기능 PR은 최소한 다음 검증을 포함한다.
- controller 단위의 MockMvc 테스트
- `./back/gradlew -p back check`
- `npm --prefix front run typecheck`
- `npm --prefix front run build`
- 화면 변경이 있으면 로컬 렌더링 확인

## 첫 구현 PR

첫 구현 PR은 사용자 프로필 API와 프로필 화면 연결로 시작한다. 이 작업은 인증을 실제로 완성하지 않고, 현재 로그인 사용자를 나타내는 데모 프로필 응답을 제공한다.

예상 파일 범위:
- `back/src/main/java/com/aquilabuddy/domain/user/**`
- `back/src/main/java/com/aquilabuddy/application/user/**`
- `back/src/main/java/com/aquilabuddy/global/user/**`
- `back/src/test/java/com/aquilabuddy/domain/user/**`
- `front/src/lib/api.ts`
- `front/src/app/profile/page.tsx`

완료 후 다음 PR부터 추천 이슈 이력과 PR 초안 흐름을 순서대로 확장한다.
