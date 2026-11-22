#!/usr/bin/env node
/**
 * Captures deterministic 2x Retina README preview & workflow screenshots for Aquila Buddy
 * using the built Next.js frontend and a bounded local fixture backend over raw CDP.
 *
 * Usage:
 *   node tools/readme/capture-readme-screens.mjs
 */

import { spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { createServer, request as httpRequest } from "node:http";
import { createServer as createNetServer } from "node:net";
import { tmpdir } from "node:os";
import path from "node:path";

const REPO_ROOT = path.resolve(import.meta.dirname, "../..");
const FRONT_DIR = path.join(REPO_ROOT, "front");
const EXT_DIR = path.join(REPO_ROOT, "extension");
const HERO_PATH = path.join(REPO_ROOT, "front/public/images/aquila-buddy-service-preview.png");
const GALLERY_DIR = path.join(REPO_ROOT, "front/public/readme");

function envelope(data) {
  return { success: true, status: "success", data, message: "success to response" };
}

function buildFixtures() {
  const profile = {
    githubId: 10001,
    login: "aquila-dev",
    name: "Aquila Dev",
    email: "dev@aquilaxk.site",
    primaryLanguages: ["Java", "TypeScript", "Rust"],
    topics: ["spring-boot", "pgvector", "nextjs", "tauri"],
    profileReady: true,
    vectorUpdatedAt: "2026-11-22T09:15:00Z",
  };

  const issues = [
    {
      id: "issue-261",
      repoFullName: "AquilaXk/aquila-buddy",
      issueNumber: 261,
      title: "GitHub 참조 문자열 검증기 바이트 분류 LUT 및 Fail-Closed 경계 강화",
      summary: "저장소 슬래그·브랜치·경로 파싱 시 정규식 백트래킹을 없애고 256-entry 바이트 LUT 단일 패스로 통제합니다.",
      score: 0.96,
      labels: ["security", "java", "performance"],
      status: "OPEN",
      isAnalyzed: true,
      analysisResultId: "issue-261",
      reason: "Java 21 백엔드 검증 경험 및 Spring Boot 보안 가드 이력과 96% 일치합니다. [스타수: 1.4k, 최근 커밋: 2026-11-22] [주 사용 언어 일치: Java]",
      bookmarked: true,
    },
    {
      id: "issue-38412",
      repoFullName: "spring-projects/spring-boot",
      issueNumber: 38412,
      title: "Actuator SseEmitter 종료 시 빈 청크 직렬화 오버헤드 제거",
      summary: "대용량 SSE 스트리밍 완료 직전 불필요한 버퍼 복사를 줄이고 연결 해제 콜백을 안전하게 정리합니다.",
      score: 0.93,
      labels: ["enhancement", "webflux"],
      status: "OPEN",
      isAnalyzed: true,
      analysisResultId: "issue-38412",
      reason: "Spring WebFlux SSE 스트리밍 구현 및 백엔드 메모리 최적화 토픽과 일치합니다. [스타수: 75.4k, 최근 커밋: 2026-11-21] [주 사용 언어 일치: Java]",
      bookmarked: true,
    },
    {
      id: "issue-71204",
      repoFullName: "vercel/next.js",
      issueNumber: 71204,
      title: "App Router Dialog 포커스 복귀 시 스크롤 점프 방지",
      summary: "네이티브 top-layer <dialog> 닫힘 직후 이전 트리거 요소로 포커스를 안전하게 복구합니다.",
      score: 0.90,
      labels: ["area: app-router", "a11y"],
      status: "OPEN",
      isAnalyzed: true,
      analysisResultId: "issue-71204",
      reason: "Next.js 16 App Router와 키보드 명령 팔레트 접근성 구현 토픽과 일치합니다. [스타수: 127k, 최근 커밋: 2026-11-22] [주 사용 언어 일치: TypeScript]",
      bookmarked: false,
    },
    {
      id: "issue-12840",
      repoFullName: "tauri-apps/tauri",
      issueNumber: 12840,
      title: "macOS 트레이 메뉴 포커스 복원 시 최소화 윈도우 unminimize 보장",
      summary: "트레이 아이콘 좌클릭 시 숨겨졌거나 최소화된 메인 WebView 창을 즉시 전면에 복구합니다.",
      score: 0.88,
      labels: ["platform: macOS", "rust"],
      status: "OPEN",
      isAnalyzed: false,
      analysisResultId: null,
      reason: "크로스 플랫폼 데스크톱 트레이·키체인 통합 및 Rust 시스템 바인딩 이력과 일치합니다. [스타수: 88.9k, 최근 커밋: 2026-11-22] [주 사용 언어 일치: Rust]",
      bookmarked: false,
    },
    {
      id: "issue-148",
      repoFullName: "pgvector/pgvector-java",
      issueNumber: 148,
      title: "PGvector text format 직렬화 시 초기 StringBuilder 용량 사전 산정",
      summary: "1536차원 임베딩 문자열 직렬화 중 배열 재할당 횟수를 줄여 GC 압력을 낮춥니다.",
      score: 0.85,
      labels: ["perf", "pgvector"],
      status: "OPEN",
      isAnalyzed: true,
      analysisResultId: "issue-148",
      reason: "pgvector HNSW Top-K 조회 및 벡터 직렬화 최적화 이력과 관련 있습니다. [스타수: 410, 최근 커밋: 2026-11-18]",
      bookmarked: true,
    },
    {
      id: "issue-264",
      repoFullName: "AquilaXk/aquila-buddy",
      issueNumber: 264,
      title: "Chrome Extension GitHub DOM MutationObserver 프레임 배칭 지연 개선",
      summary: "pjax 전환 직후 뱃지 주입 큐를 단일 rAF 틱으로 묶어 불필요한 리플로우를 막습니다.",
      score: 0.89,
      labels: ["extension", "perf"],
      status: "OPEN",
      isAnalyzed: true,
      analysisResultId: "issue-264",
      reason: "Manifest V3 DOM 스케줄러 및 GitHub 페이지 배지 주입 구조와 일치합니다. [스타수: 1.4k, 최근 커밋: 2026-11-22]",
      bookmarked: false,
    },
    {
      id: "issue-265",
      repoFullName: "AquilaXk/aquila-buddy",
      issueNumber: 265,
      title: "PKCE S256 루프백 리스너 4KB 초과 헤더 즉시 차단 및 테스트 보강",
      summary: "127.0.0.1 임시 콜백 소켓에서 과대 HTTP 헤더 수신 시 413 응답으로 즉시 연결을 닫습니다.",
      score: 0.86,
      labels: ["security", "oauth"],
      status: "OPEN",
      isAnalyzed: false,
      analysisResultId: null,
      reason: "RFC 8252 루프백 PKCE 인증 흐름 및 바이트 상한 방어 규칙과 맞닿아 있습니다. [스타수: 1.4k, 최근 커밋: 2026-11-22]",
      bookmarked: false,
    },
    {
      id: "issue-266",
      repoFullName: "AquilaXk/aquila-buddy",
      issueNumber: 266,
      title: "HybridIssueRrfScorer 어휘 스캔 시 ASCII 소문자 폴딩 룩업 적용",
      summary: "BM25 점수 계산에서 Locale 의존 toLowerCase 대신 정적 바이트 테이블로 변환합니다.",
      score: 0.83,
      labels: ["search", "performance"],
      status: "OPEN",
      isAnalyzed: false,
      analysisResultId: null,
      reason: "Okapi BM25 + RRF 검색 랭킹 파이프라인 최적화 경험과 83% 일치합니다. [스타수: 1.4k, 최근 커밋: 2026-11-21]",
      bookmarked: false,
    },
  ];

  const guide261 = {
    issueKey: "AquilaXk/aquila-buddy#261",
    filePaths: [
      "back/src/main/java/com/aquilabuddy/domain/github/GitHubReferenceValidator.java",
      "back/src/main/java/com/aquilabuddy/adapter/out/github/PullRequestDraftRequest.java",
      "front/src/lib/githubQuickPullUrl.ts",
      "back/src/test/java/com/aquilabuddy/domain/github/GitHubReferenceValidatorTest.java",
    ],
    guideline: [
      "1. GitHubReferenceValidator에 256-entry 정적 byte[] 플래그 테이블(CHAR_LUT)을 선언해 저장소 슬래그와 브랜치명을 0-할당 단일 패스로 판정합니다.",
      "2. 상위 디렉터리 이동('..'), 널 바이트('\\0'), ASCII 제어 문자가 포함되면 즉시 IllegalArgumentException을 던져 Fail-Closed로 차단합니다.",
      "3. 대문자 포크 소유자명과 점('.') 포함 릴리즈 브랜치(예: v0.1.0-rc.1)는 정상 통과하도록 허용 마스크를 분리합니다.",
      "4. 프론트엔드 buildSafeGitHubCompareUrl과 동일한 URL 길이 상한(4,096자) 및 슬래그 규칙을 양쪽 테스트에 고정합니다.",
      "5. 수정 후 ./back/gradlew -p back check --no-daemon 및 npm --prefix front run typecheck로 회귀 여부를 검증합니다.",
    ].join("\n"),
    pseudoCode: [
      "private static final byte FLAG_SLUG = 1;",
      "private static final byte FLAG_PATH = 2;",
      "private static final byte[] CHAR_LUT = new byte[256];",
      "",
      "public static String validateRepoSlug(String raw) {",
      "  if (raw == null || raw.length() > 128) throw new IllegalArgumentException(\"invalid\");",
      "  int slashCount = 0;",
      "  for (int i = 0; i < raw.length(); i++) {",
      "    char c = raw.charAt(i);",
      "    if (c == '/') { slashCount++; continue; }",
      "    if (c >= 128 || (CHAR_LUT[c] & FLAG_SLUG) == 0) {",
      "      throw new IllegalArgumentException(\"rejected char\");",
      "    }",
      "  }",
      "  if (slashCount != 1) throw new IllegalArgumentException(\"expected owner/repo\");",
      "  return raw;",
      "}",
    ].join("\n"),
    sideEffects: [
      "기존 대문자 포함 포크 소유자명이나 점(.)이 들어간 릴리즈 브랜치 명칭이 거부되지 않도록 허용 비트마스크를 분리해야 합니다.",
      "URL 조립 직전 단계에서만 1-pass 순회하므로 GitHub API 응답 파싱 지연 시간과 힙 할당량이 늘지 않습니다.",
      "백엔드 검증 강화 시 프론트엔드 buildSafeGitHubCompareUrl의 최대 길이 상한과 불일치가 생기지 않게 함께 검증해야 합니다.",
    ],
  };

  const diffContent = [
    "diff --git a/back/src/main/java/com/aquilabuddy/domain/github/GitHubReferenceValidator.java b/back/src/main/java/com/aquilabuddy/domain/github/GitHubReferenceValidator.java",
    "--- a/back/src/main/java/com/aquilabuddy/domain/github/GitHubReferenceValidator.java",
    "+++ b/back/src/main/java/com/aquilabuddy/domain/github/GitHubReferenceValidator.java",
    "@@ -18,6 +18,9 @@ final class GitHubReferenceValidator {",
    "   static final byte[] LUT = new byte[256];",
    " ",
    "   static {",
    "+    markRange('0', '9', F_SLUG);",
    "+    markRange('a', 'z', F_SLUG);",
    "+    markRange('A', 'Z', F_SLUG);",
    "-    LUT['-'] = F_DASH;",
    "+    LUT['-'] = F_SLUG | F_PATH;",
    "+    LUT['_'] = F_SLUG | F_PATH;",
    "+    LUT['.'] = F_DOT;",
    "   }",
    "diff --git a/back/src/test/java/com/aquilabuddy/domain/github/GitHubReferenceValidatorTest.java b/back/src/test/java/com/aquilabuddy/domain/github/GitHubReferenceValidatorTest.java",
    "--- a/back/src/test/java/com/aquilabuddy/domain/github/GitHubReferenceValidatorTest.java",
    "+++ b/back/src/test/java/com/aquilabuddy/domain/github/GitHubReferenceValidatorTest.java",
    "@@ -42,4 +42,6 @@ class GitHubReferenceValidatorTest {",
    "   @Test void rejectsBadRef() {",
    "-    // todo traversal guard",
    "+    assertFail(() -> check(\"../env\"));",
    "+    assertOk(checkSlug(\"org/app\"));",
    "   }",
  ].join("\n");

  const drafts = [
    {
      id: "draft-261",
      upstreamRepo: "AquilaXk/aquila-buddy",
      githubIssueNumber: 261,
      sourceIssueId: "issue-261",
      baseBranch: "main",
      headBranch: "fix/github-ref-lut-guard",
      title: "[Security] GitHubReferenceValidator 256-entry 바이트 LUT 적용 및 경로 traversal 차단",
      body: "## 관련 이슈\nclose #261\n\n## 변경 요약\n- 정규식 백트래킹 위험을 없애기 위해 256-entry `byte[]` 비트 플래그 테이블 단일 패스 순회로 치환했습니다.\n- ASCII 제어 문자, `..` 상위 경로 이동, 널 바이트가 포함된 입력을 Fail-Closed로 거부합니다.\n- 포크 소유자 대문자 및 `v0.1.0` 형태 릴리즈 브랜치 이름을 허용하는 비트 마스크를 분리했습니다.\n\n## 영향 범위\n- Backend: `GitHubReferenceValidator`, `PullRequestDraftRequest`\n- Frontend: `buildSafeGitHubCompareUrl` (4,096자 URL 상한 동기화)\n\n## 검증 방법\n- `./back/gradlew -p back check --no-daemon`\n- `npm --prefix front run typecheck && npm --prefix front run test:e2e`",
      diffContent,
      createdAt: "2026-11-22T09:40:00Z",
    },
    {
      id: "draft-38412",
      upstreamRepo: "spring-projects/spring-boot",
      githubIssueNumber: 38412,
      sourceIssueId: "issue-38412",
      baseBranch: "main",
      headBranch: "perf/sse-empty-chunk-guard",
      title: "[Perf] SseEmitter 완료 직전 빈 청크 버퍼 할당 제거",
      body: "## 관련 이슈\nclose #38412\n\n## 요약\n- SSE 스트림 완료 시 빈 바이트 배열 직렬화를 생략하고 연결 해제 훅을 정리합니다.",
      diffContent,
      createdAt: "2026-11-22T08:10:00Z",
    },
    {
      id: "draft-71204",
      upstreamRepo: "vercel/next.js",
      githubIssueNumber: 71204,
      sourceIssueId: "issue-71204",
      baseBranch: "canary",
      headBranch: "fix/dialog-focus-restore",
      title: "[Fix] App Router top-layer <dialog> 종료 시 트리거 포커스 복원",
      body: "## 관련 이슈\nclose #71204\n\n## 요약\n- 모달 종료 시 스크롤 점프 없이 트리거 버튼으로 포커스를 복귀합니다.",
      diffContent,
      createdAt: "2026-11-21T19:20:00Z",
    },
    {
      id: "draft-12840",
      upstreamRepo: "tauri-apps/tauri",
      githubIssueNumber: 12840,
      sourceIssueId: "issue-12840",
      baseBranch: "dev",
      headBranch: "fix/macos-tray-unminimize",
      title: "[Fix] macOS 트레이 메뉴 클릭 시 최소화 창 unminimize 복구",
      body: "## 관련 이슈\nclose #12840\n\n## 요약\n- 트레이 클릭 시 숨겨진 메인 WebView를 unminimize 후 포커스합니다.",
      diffContent,
      createdAt: "2026-11-21T15:05:00Z",
    },
    {
      id: "draft-148",
      upstreamRepo: "pgvector/pgvector-java",
      githubIssueNumber: 148,
      sourceIssueId: "issue-148",
      baseBranch: "master",
      headBranch: "perf/pgvector-builder-capacity",
      title: "[Perf] PGvector 문자열 직렬화 초기 StringBuilder 용량 사전 산정",
      body: "## 관련 이슈\nclose #148\n\n## 요약\n- 1536차원 기준 예상 버퍼 길이를 사전 할당합니다.",
      diffContent,
      createdAt: "2026-11-20T22:15:00Z",
    },
  ];

  const readiness261 = {
    draftId: "draft-261",
    sourceIssueId: "issue-261",
    readyCount: 7,
    items: [
      { key: "branch-flow", label: "브랜치 흐름", description: "base/head 브랜치", detail: "main ← fix/github-ref-lut-guard", status: "READY" },
      { key: "issue-link", label: "이슈 연결 구문", description: "이슈 자동 연계", detail: "close #261 포함", status: "READY" },
      { key: "template-structure", label: "템플릿 구조 준수", description: "섹션 구성 확인", detail: "본문 섹션 준비됨", status: "READY" },
      { key: "test-plan", label: "테스트 계획", description: "검증 방법 명시", detail: "Gradle check + E2E 포함됨", status: "READY" },
      { key: "clean-diff", label: "클린 Diff 검증", description: "충돌/비밀값 검사", detail: "충돌·비밀값 미검출", status: "READY" },
      { key: "breaking-change", label: "브레이킹 체인지 확인", description: "호환성 체크", detail: "기존 포크 브랜치 호환", status: "READY" },
      { key: "title-convention", label: "제목 규격", description: "PR 제목 접두사", detail: "[Security] 규격 충족", status: "READY" },
    ],
  };

  const translation261 = {
    id: "trans-261",
    draftId: "draft-261",
    sourceIssueId: "issue-261",
    targetLanguage: "en",
    title: "[Security] Apply 256-entry byte LUT and Fail-Closed bounds in GitHubReferenceValidator",
    body: "## Related Issue\nclose #261\n\n## Summary\n- Replaced regex backtracking with a single-pass 256-entry byte[] flag LUT.\n- Rejected control characters, traversal segments, and null bytes Fail-Closed.\n\n## Verification\n- `./back/gradlew -p back check --no-daemon`",
    createdAt: "2026-11-22T09:42:00Z",
  };

  return { profile, issues, guide261, drafts, readiness261, translation261 };
}

async function startFixtureApi(port = 18080) {
  const f = buildFixtures();
  const server = createServer((req, res) => {
    const url = new URL(req.url ?? "/", "http://127.0.0.1");
    const origin = req.headers.origin;
    const method = req.method ?? "GET";
    const sendJson = (body, status = 200) => {
      res.writeHead(status, {
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Aquila-CSRF-Token",
        "Access-Control-Allow-Methods": "GET, POST, PATCH, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Origin": origin ?? "*",
        "Access-Control-Allow-Credentials": "true",
        "Cache-Control": "no-store",
        "Content-Type": "application/json; charset=utf-8",
      });
      res.end(JSON.stringify(body));
    };

    if (method === "OPTIONS") {
      res.writeHead(204, {
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Aquila-CSRF-Token",
        "Access-Control-Allow-Methods": "GET, POST, PATCH, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Origin": origin ?? "*",
        "Access-Control-Allow-Credentials": "true",
      });
      res.end();
      return;
    }

    const p = url.pathname;
    if (p === "/api/v1/users/me") return sendJson(envelope(f.profile));
    if (p === "/api/v1/issues/recommend" || p === "/api/v1/issues/recommend/history") {
      return sendJson(envelope(f.issues));
    }
    if (p === "/api/v1/issues/bookmarks") {
      return sendJson(envelope(f.issues.filter((i) => i.bookmarked)));
    }
    if (p === "/api/v1/issues/issue-261") return sendJson(envelope(f.issues[0]));
    if (p === "/api/v1/issues/issue-261/guide") return sendJson(envelope(f.guide261));
    if (p === "/api/v1/issues/issue-261/guide/stream") {
      res.writeHead(200, {
        "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Aquila-CSRF-Token",
        "Access-Control-Allow-Origin": origin ?? "*",
        "Access-Control-Allow-Credentials": "true",
        "Content-Type": "text/event-stream; charset=utf-8",
      });
      res.write(`event: chunk\ndata: ${JSON.stringify({ step: "FILES", filePaths: f.guide261.filePaths })}\n\n`);
      res.write(`event: chunk\ndata: ${JSON.stringify({ step: "GUIDELINE", chunk: f.guide261.guideline })}\n\n`);
      res.write(`event: chunk\ndata: ${JSON.stringify({ step: "PSEUDO_CODE", chunk: f.guide261.pseudoCode })}\n\n`);
      res.write(`event: chunk\ndata: ${JSON.stringify({ step: "SIDE_EFFECTS", sideEffects: f.guide261.sideEffects })}\n\n`);
      res.write(`event: complete\ndata: ${JSON.stringify(f.guide261)}\n\n`);
      res.end();
      return;
    }
    if (p === "/api/v1/issues/issue-261/pseudo") {
      return sendJson(envelope({ issueKey: f.guide261.issueKey, pseudoCode: f.guide261.pseudoCode }));
    }
    if (p === "/api/v1/pr") return sendJson(envelope(f.drafts));
    if (p === "/api/v1/pr/history") {
      return sendJson(
        envelope({
          content: f.drafts.slice(0, 3),
          page: 0,
          size: 5,
          totalElements: f.drafts.length,
          totalPages: 2,
          first: true,
          last: false,
        })
      );
    }
    if (p === "/api/v1/pr/draft-261") return sendJson(envelope(f.drafts[0]));
    if (p === "/api/v1/pr/draft-261/readiness") return sendJson(envelope(f.readiness261));
    if (p === "/api/v1/pr/draft-261/translations") return sendJson(envelope([f.translation261]));
    if (p === "/api/v1/pr/draft-261/submission") {
      return sendJson(
        envelope({
          draftId: "draft-261",
          upstreamRepo: "AquilaXk/aquila-buddy",
          githubIssueNumber: 261,
          sourceIssueId: "issue-261",
          baseBranch: "main",
          headBranch: "fix/github-ref-lut-guard",
          items: [],
        })
      );
    }
    if (p === "/api/v1/pr/draft-261/timeline") {
      return sendJson(envelope({ draftId: "draft-261", sourceIssueId: "issue-261", items: [] }));
    }
    return sendJson({ success: false, status: "fail", data: null, message: `unhandled ${method} ${p}` }, 404);
  });

  await new Promise((resolveListen, rejectListen) => {
    server.once("error", rejectListen);
    server.listen(port, "127.0.0.1", () => {
      server.off("error", rejectListen);
      resolveListen();
    });
  });
  return server;
}

async function getFreePort() {
  const s = createNetServer();
  await new Promise((r) => s.listen(0, "127.0.0.1", r));
  const { port } = s.address();
  await new Promise((r) => s.close(r));
  return port;
}

async function startNextServer(frontDir, port, apiPort) {
  const child = spawn("npm", ["run", "start", "--", "-p", String(port), "-H", "127.0.0.1"], {
    cwd: frontDir,
    env: { ...process.env, NEXT_PUBLIC_API_URL: `http://127.0.0.1:${apiPort}`, PORT: String(port) },
    stdio: ["ignore", "pipe", "pipe"],
  });
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/login`);
      if (res.ok) return child;
    } catch {
      // Next.js 기동 대기
    }
    await new Promise((r) => setTimeout(r, 200));
  }
  child.kill("SIGKILL");
  throw new Error(`Timed out starting Next.js in ${frontDir}`);
}

async function startExtensionFixtureServer() {
  const html = `<!doctype html>
  <html lang="en">
    <head>
      <meta charset="utf-8" />
      <title>Issues · AquilaXk/aquila-buddy</title>
      <style>
        body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Segoe UI", Helvetica, Arial, sans-serif; background: #ffffff; color: #1f2328; }
        .gh-header { background: #f6f8fa; border-bottom: 1px solid #d0d7de; padding: 14px 32px 0; }
        .gh-repo { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
        .gh-repo-left { display: flex; align-items: center; gap: 10px; font-size: 18px; }
        .gh-badge { border: 1px solid #d0d7de; border-radius: 9999px; padding: 1px 9px; font-size: 12px; color: #57606a; font-weight: 500; }
        .gh-stats { display: flex; gap: 8px; font-size: 12px; color: #57606a; }
        .gh-stat-pill { border: 1px solid #d0d7de; border-radius: 6px; background: #fff; padding: 3px 10px; font-weight: 600; color: #1f2328; }
        .gh-nav { display: flex; gap: 20px; font-size: 14px; color: #1f2328; }
        .gh-nav span { padding: 8px 4px 10px; border-bottom: 2px solid transparent; }
        .gh-nav span.active { font-weight: 600; border-bottom-color: #fd8c73; }
        .gh-main { margin: 20px 390px 24px 32px; }
        .gh-search-row { display: flex; gap: 8px; margin-bottom: 14px; }
        .gh-search-box { flex: 1; border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; padding: 6px 12px; font-size: 13px; color: #57606a; font-family: ui-monospace, monospace; }
        .gh-btn { border: 1px solid #d0d7de; border-radius: 6px; background: #f6f8fa; padding: 6px 12px; font-size: 13px; font-weight: 600; color: #1f2328; }
        .gh-btn-green { background: #1f883d; border-color: rgba(31,35,40,0.15); color: #ffffff; }
        .gh-list { border: 1px solid #d0d7de; border-radius: 6px; overflow: hidden; }
        .gh-toolbar { background: #f6f8fa; border-bottom: 1px solid #d0d7de; padding: 11px 16px; font-size: 13px; font-weight: 600; display: flex; justify-content: space-between; align-items: center; }
        .gh-toolbar-left { display: flex; gap: 16px; }
        .gh-toolbar-right { display: flex; gap: 16px; font-weight: 500; color: #57606a; font-size: 12.5px; }
        .js-issue-row { padding: 12px 16px; border-bottom: 1px solid #d8dee4; display: flex; flex-direction: column; gap: 5px; }
        .js-issue-row:last-child { border-bottom: none; }
        .js-issue-row a.title-link { color: #1f2328; font-weight: 600; font-size: 14.5px; text-decoration: none; }
        .label-pill { display: inline-block; padding: 1px 7px; border-radius: 12px; font-size: 11px; font-weight: 600; border: 1px solid #d0d7de; margin-left: 5px; background: #eef4fe; color: #0969da; }
        .label-perf { background: #fff8c5; color: #9a6700; border-color: rgba(154,103,0,0.28); }
        .label-sec { background: #ffebe9; color: #cf222e; border-color: rgba(207,34,46,0.25); }
        .issue-meta { font-size: 12px; color: #656d76; }
      </style>
    </head>
    <body>
      <header class="gh-header">
        <div class="gh-repo">
          <div class="gh-repo-left">
            <span><strong>AquilaXk / aquila-buddy</strong></span>
            <span class="gh-badge">Public</span>
          </div>
          <div class="gh-stats">
            <span class="gh-stat-pill">★ Star 1.4k</span>
            <span class="gh-stat-pill">Fork 128</span>
          </div>
        </div>
        <nav class="gh-nav">
          <span>&lt;&gt; Code</span>
          <span class="active">⊙ Issues 14</span>
          <span>⑂ Pull requests 4</span>
          <span>▷ Actions</span>
          <span>🛡 Security</span>
        </nav>
      </header>
      <main id="repo-content-pjax-container" class="gh-main">
        <div class="gh-search-row">
          <div class="gh-search-box">is:issue is:open label:"good first issue" sort:updated-desc</div>
          <span class="gh-btn">Labels 12</span>
          <span class="gh-btn gh-btn-green">New issue</span>
        </div>
        <div class="gh-list">
          <div class="gh-toolbar">
            <div class="gh-toolbar-left"><span>⊙ 14 Open</span><span>✓ 64 Closed</span></div>
            <div class="gh-toolbar-right"><span>Author ▾</span><span>Label ▾</span><span>Language ▾</span><span>Sort ▾</span></div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/261">GitHub 참조 문자열 검증기 바이트 분류 LUT 및 Fail-Closed 경계 강화</a><span class="label-pill label-sec">security</span><span class="label-pill">java</span></div>
            <div class="issue-meta">#261 opened 2 hours ago by AquilaXk · good first issue · 3 comments</div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/264">Chrome Extension GitHub DOM MutationObserver 프레임 배칭 지연 개선</a><span class="label-pill">extension</span><span class="label-pill label-perf">perf</span></div>
            <div class="issue-meta">#264 opened 4 hours ago by AquilaXk · enhancement · 2 comments</div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/265">PKCE S256 루프백 리스너 4KB 초과 헤더 즉시 차단 및 테스트 보강</a><span class="label-pill label-sec">security</span><span class="label-pill">oauth</span></div>
            <div class="issue-meta">#265 opened 6 hours ago by AquilaXk · rust · 4 comments</div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/266">HybridIssueRrfScorer 어휘 스캔 시 ASCII 소문자 폴딩 룩업 적용</a><span class="label-pill label-perf">performance</span><span class="label-pill">search</span></div>
            <div class="issue-meta">#266 opened yesterday by AquilaXk · java · 1 comment</div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/267">DiffViewer 통합/분할 모드 전환 시 파일 점프 스크롤 위치 유지</a><span class="label-pill">frontend</span><span class="label-pill">a11y</span></div>
            <div class="issue-meta">#267 opened yesterday by AquilaXk · nextjs</div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/268">pgvector HNSW 인덱스 init 스크립트 idempotent 검증 케이스 추가</a><span class="label-pill">database</span><span class="label-pill">pgvector</span></div>
            <div class="issue-meta">#268 opened 2 days ago by AquilaXk · postgresql</div>
          </div>
          <div class="js-issue-row">
            <div><a class="title-link" href="/AquilaXk/aquila-buddy/issues/269">JWT HS256 클레임 스캐너 최대 중첩 깊이(32) 초과 악의적 페이로드 테스트 추가</a><span class="label-pill label-sec">security</span></div>
            <div class="issue-meta">#269 opened 3 days ago by AquilaXk · test · 2 comments</div>
          </div>
        </div>
      </main>
    </body>
  </html>`;

  const server = createServer((req, res) => {
    res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
    res.end(html);
  });
  await new Promise((r) => server.listen(0, "127.0.0.1", r));
  const { port } = server.address();
  return { origin: `http://127.0.0.1:${port}`, server };
}

async function startDesktopHeroHostServer(targetPort, issuesPort) {
  const heroHtml = `<!doctype html>
  <html lang="ko">
    <head>
      <meta charset="utf-8" />
      <title>Aquila Buddy Desktop</title>
      <style>
        * { box-sizing: border-box; }
        html, body {
          margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden;
          font-family: -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Malgun Gothic", "Segoe UI", sans-serif;
          background: linear-gradient(145deg, #eef2f6 0%, #e4eaf2 100%);
        }
        .stage {
          width: 100%; height: 100%; padding: 12px 16px; display: flex; flex-direction: column;
        }
        .window {
          flex: 1; display: flex; flex-direction: column; overflow: hidden;
          border-radius: 10px; border: 1px solid #c9d1d9; background: #ffffff;
          box-shadow: 0 18px 42px rgba(31, 35, 40, 0.14), 0 3px 10px rgba(31, 35, 40, 0.08);
        }
        .titlebar {
          height: 36px; flex-shrink: 0; background: #f6f8fa; border-bottom: 1px solid #d1d9e0;
          display: flex; align-items: center; justify-content: space-between; padding: 0 14px;
        }
        .traffic-lights { display: flex; align-items: center; gap: 7px; width: 170px; }
        .dot { width: 11px; height: 11px; border-radius: 9999px; border: 1px solid rgba(0,0,0,0.12); }
        .dot-r { background: #ff5f57; }
        .dot-y { background: #febc2e; }
        .dot-g { background: #28c840; }
        .titlebar-center {
          font-size: 12px; font-weight: 600; color: #1f2328; letter-spacing: -0.01em;
          display: flex; align-items: center; gap: 8px;
        }
        .titlebar-pill {
          font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 10.5px; font-weight: 600;
          color: #0969da; background: #ddf4ff; border: 1px solid rgba(9, 105, 218, 0.25);
          padding: 1px 7px; border-radius: 9999px;
        }
        .titlebar-right {
          width: 170px; text-align: right; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
          font-size: 11px; color: #59636e;
        }
        .content-frame {
          flex: 1; border: 0; width: 100%; height: 100%; display: block; background: #ffffff;
        }
        .statusbar {
          height: 26px; flex-shrink: 0; background: #f6f8fa; border-top: 1px solid #d1d9e0;
          display: flex; align-items: center; justify-content: space-between; padding: 0 14px;
          font-size: 11px; color: #59636e;
          font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
        }
        .status-ok { color: #1a7f37; font-weight: 600; }
      </style>
    </head>
    <body>
      <div class="stage">
        <div class="window">
          <div class="titlebar">
            <div class="traffic-lights">
              <span class="dot dot-r"></span>
              <span class="dot dot-y"></span>
              <span class="dot dot-g"></span>
            </div>
            <div class="titlebar-center">
              <span>Aquila Buddy Desktop</span>
              <span class="titlebar-pill">v0.1.0</span>
            </div>
            <div class="titlebar-right">macOS · Windows · Linux</div>
          </div>
          <iframe class="content-frame" id="desktop-webview" src="/issues/issue-261"></iframe>
          <div class="statusbar">
            <span><span class="status-ok">● Keychain 연결됨</span> · PKCE Loopback (127.0.0.1) · Local Git Status 준비</span>
            <span>AquilaXk/aquila-buddy #261 · 96% 매칭</span>
          </div>
        </div>
      </div>
    </body>
  </html>`;

  const server = createServer((req, res) => {
    const reqUrl = new URL(req.url ?? "/", "http://127.0.0.1");
    if (reqUrl.pathname === "/hero.html") {
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
      res.end(heroHtml);
      return;
    }
    const proxyReq = httpRequest(
      {
        hostname: "127.0.0.1",
        port: issuesPort,
        path: req.url,
        method: req.method,
        headers: req.headers,
      },
      (proxyRes) => {
        res.writeHead(proxyRes.statusCode ?? 200, proxyRes.headers);
        proxyRes.pipe(res, { end: true });
      }
    );
    proxyReq.on("error", (err) => {
      if (!res.headersSent) {
        res.writeHead(502);
        res.end(err.message);
      }
    });
    req.pipe(proxyReq, { end: true });
  });
  await new Promise((r) => server.listen(targetPort, "127.0.0.1", r));
  return server;
}

function findChrome() {
  const candidates = [
    process.env.CHROME_BIN,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/chromium",
  ].filter(Boolean);
  const found = candidates.find((p) => existsSync(p));
  if (!found) throw new Error("Chrome binary not found");
  return found;
}

async function openCdpSession(chromePort) {
  const targetRes = await fetch(`http://127.0.0.1:${chromePort}/json/new?about:blank`, { method: "PUT" });
  const target = await targetRes.json();
  const ws = new WebSocket(target.webSocketDebuggerUrl);
  const pending = new Map();
  let nextId = 1;
  await new Promise((resolveWs, rejectWs) => {
    ws.addEventListener("open", resolveWs, { once: true });
    ws.addEventListener("error", rejectWs, { once: true });
  });
  ws.addEventListener("message", (ev) => {
    const msg = JSON.parse(ev.data);
    if (msg.id && pending.has(msg.id)) {
      const { resolve: resP, reject: rejP } = pending.get(msg.id);
      pending.delete(msg.id);
      if (msg.error) rejP(new Error(msg.error.message));
      else resP(msg.result);
    }
  });
  const send = (method, params = {}) => {
    const id = nextId++;
    ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resP, rejP) => pending.set(id, { resolve: resP, reject: rejP }));
  };
  await send("Page.enable");
  await send("Runtime.enable");
  await send("Emulation.setDeviceMetricsOverride", {
    width: 1440,
    height: 900,
    deviceScaleFactor: 2,
    mobile: false,
  });
  return { ws, send };
}

async function evalJs(send, expression) {
  const result = await send("Runtime.evaluate", { expression, awaitPromise: true, returnByValue: true });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text + " :: " + (result.exceptionDetails.exception?.description ?? ""));
  }
  return result.result?.value;
}

async function waitForExpr(send, expression, label) {
  const deadline = Date.now() + 20_000;
  while (Date.now() < deadline) {
    if (await evalJs(send, expression)) return;
    await new Promise((r) => setTimeout(r, 150));
  }
  throw new Error(`Timeout waiting for ${label}`);
}

async function capturePage(send, url, waitExpr, outputPath, afterLoadFn) {
  await send("Page.navigate", { url });
  await waitForExpr(send, `document.readyState === "complete" && (${waitExpr})`, outputPath);
  if (afterLoadFn) {
    await afterLoadFn();
  }
  await evalJs(send, `document.fonts?.ready?.then(() => true)`);
  await new Promise((r) => setTimeout(r, 250));
  const { data } = await send("Page.captureScreenshot", { format: "png" });
  await writeFile(outputPath, Buffer.from(data, "base64"));
}

async function main() {
  await mkdir(path.dirname(HERO_PATH), { recursive: true });
  await mkdir(GALLERY_DIR, { recursive: true });

  const fixtureApiPort = Number(process.env.README_FIXTURE_API_PORT ?? "18080");
  const apiServer = await startFixtureApi(fixtureApiPort);
  const userDataDir = await mkdtemp(path.join(tmpdir(), "aquila-readme-shots-"));
  const chromePort = await getFreePort();
  const chrome = spawn(
    findChrome(),
    [
      "--headless=new",
      "--disable-gpu",
      "--no-sandbox",
      "--no-first-run",
      "--no-default-browser-check",
      "--remote-debugging-address=127.0.0.1",
      `--remote-debugging-port=${chromePort}`,
      `--user-data-dir=${userDataDir}`,
      "about:blank",
    ],
    { stdio: "ignore" }
  );

  for (let i = 0; i < 60; i += 1) {
    try {
      const r = await fetch(`http://127.0.0.1:${chromePort}/json/version`);
      if (r.ok) break;
    } catch {
      // Chrome 기동 대기
    }
    await new Promise((r) => setTimeout(r, 200));
  }

  let nextChild = null;
  let heroHostServer = null;

  try {
    const nextPort = await getFreePort();
    const heroPort = await getFreePort();
    nextChild = await startNextServer(FRONT_DIR, nextPort, fixtureApiPort);
    heroHostServer = await startDesktopHeroHostServer(heroPort, nextPort);
    const { ws, send } = await openCdpSession(chromePort);

    try {
      await send("Page.navigate", { url: `http://127.0.0.1:${nextPort}/login` });
      await waitForExpr(send, `document.readyState === "complete"`, "init storage");
      await evalJs(
        send,
        `localStorage.setItem("aquila-buddy-authenticated", "true"); localStorage.setItem("aquila-buddy-token", "demo-jwt");`
      );

      // 1. Dashboard Overview
      await capturePage(
        send,
        `http://127.0.0.1:${nextPort}/dashboard`,
        `document.body.innerText.includes("pgvector/pgvector-java") && document.body.innerText.includes("추천 기준 동기화")`,
        path.join(GALLERY_DIR, "dashboard-overview.png")
      );

      // 2. Issue Recommendations with active j/k row highlight
      await capturePage(
        send,
        `http://127.0.0.1:${nextPort}/issues`,
        `document.body.innerText.includes("GitHub 참조 문자열 검증기")`,
        path.join(GALLERY_DIR, "issue-recommendations.png"),
        async () => {
          await evalJs(send, `window.dispatchEvent(new KeyboardEvent("keydown", { key: "j", bubbles: true }))`);
        }
      );

      // 3. Issue Code Context & Live SSE Guide
      await capturePage(
        send,
        `http://127.0.0.1:${nextPort}/issues/issue-261`,
        `document.body.innerText.includes("힌트 보기")`,
        path.join(GALLERY_DIR, "issue-code-analysis.png"),
        async () => {
          await evalJs(
            send,
            `Array.from(document.querySelectorAll("button")).find(b => b.innerText.includes("힌트 보기"))?.click()`
          );
          await waitForExpr(
            send,
            `document.body.innerText.includes("256-entry 정적 byte[]") && document.body.innerText.includes("허용 비트마스크를 분리")`,
            "issue guide stream completion"
          );
        }
      );

      // 4. PR Draft Split Diff & Translation (collapsed sidebar for full split diff width)
      await capturePage(
        send,
        `http://127.0.0.1:${nextPort}/pr/draft-261`,
        `document.body.innerText.includes("GitHubReferenceValidator")`,
        path.join(GALLERY_DIR, "pr-draft-diff.png"),
        async () => {
          await evalJs(
            send,
            `document.querySelector('button[aria-label="사이드바 접기"]')?.click(); Array.from(document.querySelectorAll("button")).find(b => b.innerText.trim() === "분할")?.click();`
          );
          await new Promise((r) => setTimeout(r, 200));
        }
      );

      // 5. Hero Preview inside macOS native desktop frame (same-origin proxy to share localStorage)
      await send("Page.navigate", { url: `http://127.0.0.1:${heroPort}/login` });
      await waitForExpr(send, `document.readyState === "complete"`, "hero origin storage");
      await evalJs(
        send,
        `localStorage.setItem("aquila-buddy-authenticated", "true"); localStorage.setItem("aquila-buddy-token", "demo-jwt");`
      );
      await capturePage(
        send,
        `http://127.0.0.1:${heroPort}/hero.html`,
        `document.getElementById("desktop-webview")?.contentDocument?.body?.innerText?.includes("힌트 보기")`,
        HERO_PATH,
        async () => {
          await evalJs(
            send,
            `(() => {
              const doc = document.getElementById("desktop-webview").contentDocument;
              Array.from(doc.querySelectorAll("button"))
                .find((b) => b.innerText.trim() === "힌트 보기")
                ?.click();
            })()`
          );
          await waitForExpr(
            send,
            `document.getElementById("desktop-webview")?.contentDocument?.body?.innerText?.includes("256-entry 정적 byte[]")`,
            "hero webview guide stream"
          );
        }
      );

      // 6. Chrome Extension Docked Sidebar on GitHub Issues
      const extFixture = await startExtensionFixtureServer();
      try {
        const f = buildFixtures();
        const extMatches = f.issues.filter((issue) => issue.repoFullName === "AquilaXk/aquila-buddy");
        await capturePage(
          send,
          `${extFixture.origin}/AquilaXk/aquila-buddy/issues`,
          `document.body.innerText.includes("GitHub 참조 문자열 검증기")`,
          path.join(GALLERY_DIR, "github-extension-dock.png"),
          async () => {
            const [contentCss, contentJs] = await Promise.all([
              readFile(path.join(EXT_DIR, "content.css"), "utf8"),
              readFile(path.join(EXT_DIR, "content.js"), "utf8"),
            ]);
            await evalJs(
              send,
              `(() => {
                const fixtures = ${JSON.stringify({ rec: extMatches, guide: f.guide261 })};
                window.chrome = {
                  storage: { local: { get: async () => ({}), set: async () => {} } },
                  runtime: {
                    sendMessage(msg, cb) {
                      if (msg.type === "GET_CACHED_RECOMMENDATIONS" || msg.type === "GET_RECOMMEND_ISSUES") {
                        queueMicrotask(() => cb({ ok: true, data: { data: fixtures.rec } }));
                      } else if (msg.type === "GET_ISSUE_GUIDE") {
                        queueMicrotask(() => cb({ ok: true, data: { data: fixtures.guide } }));
                      } else {
                        queueMicrotask(() => cb({ ok: true, data: {} }));
                      }
                    },
                    onMessage: { addListener() {} },
                  },
                };
                const st = document.createElement("style");
                st.textContent = ${JSON.stringify(contentCss)};
                document.head.appendChild(st);
              })();`
            );
            await evalJs(send, contentJs);
            await waitForExpr(send, `Boolean(document.querySelector(".aquila-buddy-badge"))`, "badge injection");
            await evalJs(
              send,
              `document.querySelector("aquila-buddy-root")?._shadowRoot?.querySelector("#aquila-buddy-toggle")?.click()`
            );
            await waitForExpr(
              send,
              `Boolean(document.querySelector("aquila-buddy-root")?._shadowRoot?.textContent?.includes("GitHub 참조"))`,
              "sidebar docked open"
            );
          }
        );
      } finally {
        extFixture.server.close();
      }
    } finally {
      ws.close();
    }
  } finally {
    if (heroHostServer) heroHostServer.close();
    if (nextChild && !nextChild.killed) {
      nextChild.kill("SIGTERM");
      await new Promise((r) => nextChild.once("exit", r));
    }
    apiServer.close();
    chrome.kill("SIGTERM");
    await new Promise((r) => chrome.once("exit", r));
    await rm(userDataDir, { recursive: true, force: true });
  }
}

await main();
console.log("Captured all Aquila Buddy README hero and workflow screenshots.");
