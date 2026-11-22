# Third-Party Runtime & Open Standards Cleanroom Notices

이 문서는 `Aquila Buddy`가 사용하는 코어 플랫폼 런타임 외 애플리케이션 레벨 구성 요소의 공개 표준 기반 클린룸 구현 현황을 기록한다.

## 무의존성 클린룸 설계 원칙 (QA 2026-11-22)

1. **코어 런타임 경계**: Java 21 · Spring Boot 3.5, PostgreSQL 16 · pgvector, Google GenAI SDK(`google-genai`), Next.js 16 · React 19(Tailwind CSS 컴파일 타임 빌드), 데스크톱 셸 `Tauri v2 · tao · wry · keyring` (`MIT OR Apache-2.0`)만 코어 플랫폼 런타임으로 사용한다.
2. **외부 UI · 유틸 · 웹폰트 · JWT 패키지 제거**: `@primer/primitives`, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react`, `geist`, `pretendard`, `io.jsonwebtoken:jjwt-*`, `com.pgvector:pgvector`를 모두 제거했다(`front/package.json` 런타임 의존성 3개: `next`, `react`, `react-dom`).
3. **공개 표준 기반 자체 클린룸 구현**: 아래 구성 요소는 외부 패키지나 외부 UI/유틸 소스 없이 IETF·NIST·IEEE·W3C·HTML Living Standard 공개 명세만을 근거로 직접 구현했다.

## Cleanroom Component Inventory

| Module / Feature | Standard / Specification Basis | Target Path | Implementation Summary |
| :--- | :--- | :--- | :--- |
| **RFC 7519 `HS256` JWT 공급자** | IETF RFC 7519, RFC 7518 (`HS256`), FIPS 198-1, Java 21 `javax.crypto.Mac` | `back/src/main/java/com/aquilabuddy/global/auth/jwt/JwtProvider.java` | 32바이트(256-bit) 최소 시크릿 검증, `ThreadLocal<Mac>` 풀링, 상수시간(`MessageDigest.isEqual`) 서명 검증, `alg=HS256` 강제, 자체 1-pass JSON 클레임 스캐너(`MAX_JSON_DEPTH = 32`, `MAX_TOKEN_LENGTH = 4_096`). |
| **PKCE (`S256`) & SHA-256** | IETF RFC 7636, NIST FIPS 180-4 | `back/.../ExtensionOAuthExchangeService.java`, `extension/background.js` | 1회용 코드(`60s` TTL, 최대 `1024` 엔트리, `MessageDigest.isEqual` 검증) 및 WebCrypto `crypto.subtle` + 순수 JS FIPS 180-4 SHA-256 fallback. |
| **고성능 벡터 내적 값 객체** | IEEE 754 `fma`, 선형대수 L2 코사인 유사도 | `back/src/main/java/com/aquilabuddy/domain/embedding/EmbeddingVector.java` | 원시 `double[]`, 역노름($1/\|\mathbf{v}\|_2$) 사전 계산, 4-way 루프 언롤링 `Math.fma` 내적, `MAX_DIMENSIONS = 8_192` 입력 상한. |
| **준중복 이슈 지문** | Charikar 2002 LSH, FNV-1a 64-bit | `back/.../IssueFingerprintSimHash.java` | 정규식 분할 없는 1-pass ASCII 폴딩 + 64-bit FNV-1a 토큰 해시와 `Long.bitCount` 해밍 거리 근접 중복 제거. |
| **하이브리드 정보 검색** | Okapi BM25, Reciprocal Rank Fusion | `back/.../HybridIssueRrfScorer.java` | 단일 패스 어휘 스캔(`MAX_QUERY_TERMS = 32`, `MAX_DOC_SCAN_CHARS = 4096`), `k1 = 1.2`, `b = 0.75`, `1 / (60 + rank)` 융합. |
| **64-bit 비트마스크 퍼지 탐색** | Wu-Manber / Shift-Or 비트 병렬 매칭 | `front/src/lib/bitapSearch.ts` | `Int32Array(128)` / `Uint32Array(256)` 1회 사전 컴파일과 최대 1오타 매칭 + 1-pass 집계(`filterAndAggregateIssues`). |
| **Unified Diff 상태 머신** | GNU Unified Diff 형식 | `front/src/lib/parseUnifiedDiff.ts` | `indexOf("\n")` 단일 패스 상태 머신 파싱, `MAX_DIFF_PARSE_CHARS = 65_536` Fail-Closed 상한. |
| **명령 팔레트 & 키보드 위젯** | WHATWG `<dialog>`, WAI-ARIA APG (`combobox`, `tablist`, Splitter) | `front/src/components/{CommandPalette,StudioSplitPane,LanguageTabSwitcher}.tsx`, `front/src/components/ui/tabs.tsx` | 네이티브 `<dialog>` `showModal()`, `closedby="any"`, `role="combobox"`/`listbox`/`tablist`/`separator` 키보드 내비게이션, IME 확정 Enter 방어. |
| **무할당 클래스 결합 유틸** | TypeScript 1-pass 문자열 결합 | `front/src/lib/utils.ts` | 정규식 및 중간 배열 할당 없는 단일 `for` 루프 `cn()` 유틸리티. |
| **라이트 토큰 & UI 프리미티브** | CSS Custom Properties, React 19 정적 `Record` 테이블 | `front/src/app/{layout.tsx,globals.css}`, `front/tailwind.config.ts`, `front/src/components/ui/{badge,button,card,kbd,tabs}.tsx` | 시스템 네이티브 폰트 스택(`--font-sans`, `--font-mono`, 웹폰트 전송 0KB), 시맨틱 라이트 컬러/Diff 토큰, 순수 React 19 UI 프리미티브 5종. |
| **기하학 24×24 SVG 아이콘** | W3C SVG 2 기본 도형 (`path`, `circle`, `rect`, `polygon`) | `front/src/components/icons.tsx` | 24×24 뷰박스 공통 `createIcon` 팩토리 기반 자체 아이콘 컴포넌트 40종. |
| **프레임 DOM 큐 스케줄러** | WHATWG HTML Living Standard `requestAnimationFrame` | `extension/content.js` | `domFrameQueue`(`read`/`write`/`cancelAll`) 2단계 `rAF` 배칭 및 `try/finally` 예외 격리. |
