import { createServer } from "node:http";

export async function createFrontendFixtureBackend(options = {}) {
  const state = {
    profileFailure: Boolean(options.profileFailure),
  };
  const server = createServer((request, response) => {
    const url = new URL(request.url ?? "/", "http://127.0.0.1");
    const origin = request.headers.origin;

    if (request.method === "OPTIONS") {
      writeJson(response, 204, null, origin);
      return;
    }

    if (url.pathname.endsWith("/stream")) {
      response.writeHead(200, {
        "Access-Control-Allow-Origin": origin ?? "*",
        "Access-Control-Allow-Credentials": "true",
        "Content-Type": "text/event-stream; charset=utf-8",
        "Cache-Control": "no-cache, no-transform",
        "Connection": "keep-alive"
      });
      response.write("event: chunk\ndata: " + JSON.stringify({ step: "ANALYZING", message: "저장소 코드 맥락 분석 중..." }) + "\n\n");
      response.write("event: chunk\ndata: " + JSON.stringify({ step: "FILES", filePaths: ["front/src/app/issues/[id]/page.tsx", "back/src/main/java/com/aquilabuddy/domain/analysis/IssueGuideService.java"] }) + "\n\n");
      response.write("event: chunk\ndata: " + JSON.stringify({ step: "GUIDELINE", chunk: "Signature Lavender 테마 적용과 함께 fixture backend와 연결된 화면 smoke를 구성하고, 7대 기여 준비도 게이트를 점검합니다." }) + "\n\n");
      response.write("event: complete\ndata: " + JSON.stringify({
        issueKey: "aquila-buddy#261",
        filePaths: ["front/src/app/issues/[id]/page.tsx", "back/src/main/java/com/aquilabuddy/domain/analysis/IssueGuideService.java"],
        guideline: "Signature Lavender 테마 적용과 함께 fixture backend와 연결된 화면 smoke를 구성하고, 7대 기여 준비도 게이트를 점검합니다.",
        pseudoCode: "GIVEN issue detail page renders contributor guidance\nWHEN user connects to SSE guide stream\nCALL extract_ast_symbol_skeleton()\nCALL stream_issue_guide_chunks()\nASSERT all 7 readiness gates are verified",
        sideEffects: ["CI 런타임에 브라우저 통합 smoke 검증 단계가 추가됩니다."]
      }) + "\n\n");
      response.end();
      return;
    }

    const payload = routeFixture(url, request.method ?? "GET", state);
    writeJson(response, payload.httpStatus ?? 200, payload.body, origin);
  });

  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(options.port ?? 0, "127.0.0.1", () => {
      server.off("error", reject);
      resolve();
    });
  });

  const address = server.address();
  if (!address || typeof address === "string") {
    throw new Error("fixture backend address was not assigned");
  }

  return {
    origin: `http://127.0.0.1:${address.port}`,
    setProfileFailure(value) {
      state.profileFailure = Boolean(value);
    },
    close: () => new Promise((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()));
    }),
  };
}

function routeFixture(url, method, options) {
  const pathname = url.pathname;

  if (pathname === "/api/v1/users/me" && method === "GET" && options.profileFailure) {
    return response(errorEnvelope("fixture profile failure"));
  }

  if (pathname === "/api/v1/health") {
    return response(successEnvelope({ status: "UP" }));
  }

  if (pathname === "/api/v1/users/me") {
    return response(successEnvelope(profileFixture()));
  }

  if (pathname === "/api/v1/users/me/vector") {
    return response(successEnvelope({ ...profileFixture(), profileReady: true }));
  }

  if (pathname === "/api/v1/issues/recommend/history" || pathname === "/api/v1/issues/recommend") {
    return response(successEnvelope([issueHistoryFixture(), issueFixture2(), issueFixture3()]));
  }

  if (pathname === "/api/v1/issues" || pathname === "/api/v1/issues/crawl/search") {
    return response(successEnvelope([issueFixture(), issueFixture2(), issueFixture3()]));
  }

  if (pathname === "/api/v1/issues/issue-1") {
    return response(successEnvelope(issueFixture()));
  }

  if (pathname === "/api/v1/issues/issue-1/guide") {
    return response(successEnvelope({
      issueKey: "aquila-buddy#261",
      filePaths: [
        "front/src/app/issues/[id]/page.tsx",
        "back/src/main/java/com/aquilabuddy/domain/analysis/IssueGuideService.java",
        "tools/frontend/integration-smoke/run-integration-smoke.mjs"
      ],
      guideline: "Signature Lavender 디자인 시스템 적용 상태에서 fixture backend와 연결된 화면 smoke를 추가하고, SSE 실시간 스트리밍 가이드를 연동합니다.",
      pseudoCode: "GIVEN issue detail page renders contributor guidance\nWHEN user connects to SSE guide stream\nCALL extract_ast_symbol_skeleton()\nCALL stream_issue_guide_chunks()\nASSERT all 7 readiness gates are verified",
      sideEffects: ["CI runtime이 늘어납니다."],
    }));
  }

  if (pathname === "/api/v1/issues/issue-1/pseudo") {
    return response(successEnvelope({
      issueKey: "aquila-buddy#261",
      pseudoCode: "render dashboard\nrender issues\nrender pr draft",
    }));
  }

  if (pathname === "/api/v1/issues/issue-1/guide/workflow") {
    return response(successEnvelope({
      issueKey: "aquila-buddy#261",
      readyCount: 2,
      steps: [
        workflowStep("context", "맥락 확인", "fixture 응답 확인", "READY"),
        workflowStep("smoke", "브라우저 검증", "핵심 라우트 진입", "READY"),
      ],
    }));
  }

  if (pathname === "/api/v1/pr" && method === "GET") {
    return response(successEnvelope([draftFixture()]));
  }

  if (pathname === "/api/v1/pr" && method === "POST") {
    return response(successEnvelope(draftFixture()));
  }

  if (pathname === "/api/v1/pr/history") {
    return response(successEnvelope({
      content: [draftFixture()],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }));
  }

  if (pathname === "/api/v1/pr/draft-1") {
    return response(successEnvelope(draftFixture()));
  }

  if (pathname === "/api/v1/pr/draft-1/readiness") {
    return response(successEnvelope(readinessFixture("draft-1", "issue-1")));
  }

  if (pathname === "/api/v1/pr/draft-1/submission") {
    return response(successEnvelope(submissionFixture()));
  }

  if (pathname === "/api/v1/pr/draft-1/timeline") {
    return response(successEnvelope(timelineFixture()));
  }

  if (pathname === "/api/v1/pr/draft-1/report") {
    return response(successEnvelope({
      draftId: "draft-1",
      upstreamRepo: "AquilaXk/aquila-buddy",
      githubIssueNumber: 261,
      sourceIssueId: "issue-1",
      baseBranch: "main",
      headBranch: "test/front-backend-integration-smoke",
      title: "[Test] 프론트 백엔드 통합 smoke 검증 추가",
      createdAt: "2026-05-18T12:00:00Z",
      readinessReadyCount: 4,
      readinessTotalCount: 4,
      translationCount: 1,
      latestTranslation: {
        translationId: "translation-1",
        targetLanguage: "en",
        title: "Add frontend backend integration smoke",
        createdAt: "2026-05-18T12:01:00Z",
      },
    }));
  }

  if (pathname === "/api/v1/pr/draft-1/translate") {
    return response(successEnvelope({
      titleEn: "Add frontend backend integration smoke",
      bodyEn: "## Related issue\nclose #261\n\n## Summary\nAdd browser smoke.",
      sourceIssueId: "issue-1",
      githubUrl: "https://github.com/AquilaXk/aquila-buddy/issues/261",
    }));
  }

  if (pathname === "/api/v1/pr/draft-1/translations") {
    return response(successEnvelope([translationFixture()]));
  }

  if (pathname === "/api/v1/pr/translations/activity") {
    return response(successEnvelope([{
      draftId: "draft-1",
      translationId: "translation-1",
      upstreamRepo: "AquilaXk/aquila-buddy",
      githubIssueNumber: 261,
      sourceIssueId: "issue-1",
      targetLanguage: "en",
      title: "Add frontend backend integration smoke",
      createdAt: "2026-05-18T12:01:00Z",
    }]));
  }

  if (pathname === "/api/v1/pr/draft-1/translations/translation-1/readiness") {
    return response(successEnvelope({
      ...readinessFixture("draft-1", "issue-1"),
      translationId: "translation-1",
      targetLanguage: "en",
    }));
  }

  if (pathname === "/api/v1/pr/draft-1/translations/translation-1/timeline") {
    return response(successEnvelope({
      draftId: "draft-1",
      translationId: "translation-1",
      targetLanguage: "en",
      items: [{
        key: "translation-created",
        label: "번역 생성",
        description: "영문 초안이 생성되었습니다.",
        detail: "fixture translation ready",
        status: "DONE",
        occurredAt: "2026-05-18T12:01:00Z",
      }],
    }));
  }

  if (pathname === "/api/v1/pr/draft-1/translations/translation-1/submission") {
    return response(successEnvelope({
      ...submissionFixture(),
      translationId: "translation-1",
      targetLanguage: "en",
    }));
  }

  return response(errorEnvelope(`unhandled fixture route: ${method} ${pathname}`), 404);
}

function writeJson(response, status, body, origin) {
  const headers = {
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
    "Access-Control-Allow-Origin": origin ?? "*",
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
  };

  if (origin) {
    headers["Access-Control-Allow-Credentials"] = "true";
  }

  response.writeHead(status, headers);
  response.end(body === null ? "" : JSON.stringify(body));
}

function response(body, httpStatus = 200) {
  return { body, httpStatus };
}

function successEnvelope(data) {
  return {
    success: true,
    status: "success",
    data,
    message: "success to response",
  };
}

function errorEnvelope(message) {
  return {
    success: false,
    status: "fail",
    data: null,
    message,
  };
}

function profileFixture() {
  return {
    githubId: 10001,
    login: "aquila-smoke",
    name: "Aquila Smoke",
    email: "smoke@example.com",
    primaryLanguages: ["Java", "TypeScript"],
    topics: ["spring", "nextjs"],
    profileReady: true,
    vectorUpdatedAt: "2026-05-18T12:00:00Z",
  };
}

function issueFixture() {
  return {
    id: "issue-1",
    repoFullName: "AquilaXk/aquila-buddy",
    issueNumber: 261,
    title: "프론트 백엔드 통합 smoke 검증 추가",
    summary: "fixture backend와 연결된 smoke를 추가합니다.",
    score: 0.96,
    labels: ["good first issue", "test", "frontend"],
    status: "OPEN",
    reason: "프론트와 API envelope 통합 검증이 필요합니다.",
  };
}

function issueFixture2() {
  return {
    id: "issue-2",
    repoFullName: "AquilaXk/aquila-buddy",
    issueNumber: 284,
    title: "AST 심볼 스켈레톤 추출기 토큰 절감 최적화",
    summary: "Git tree 캐시와 다중 언어 AST 파서를 결합해 LLM 컨텍스트 토큰을 70% 이상 절감합니다.",
    score: 0.92,
    labels: ["good first issue", "backend", "java"],
    status: "OPEN",
    reason: "Java 및 TypeScript 파일의 핵심 인터페이스 및 메서드 서명만 추출하여 프롬프트 효율을 극대화합니다.",
  };
}

function issueFixture3() {
  return {
    id: "issue-3",
    repoFullName: "AquilaXk/aquila-buddy",
    issueNumber: 290,
    title: "Signature Lavender 다크 모드 반응형 레이아웃 보완",
    summary: "다크 차콜(#18191D) 및 시그니처 라벤더(#5C6BC0) 테마의 모바일 드로어 및 접근성을 강화합니다.",
    score: 0.88,
    labels: ["good first issue", "ui/ux", "frontend"],
    status: "OPEN",
    reason: "모바일 뷰포트에서 상단 네비게이션 드로어가 자연스럽게 닫히도록 터치 인터랙션을 개선합니다.",
  };
}

function issueHistoryFixture() {
  return {
    ...issueFixture(),
    isAnalyzed: true,
    analysisResultId: "analysis-1",
  };
}

function draftFixture() {
  return {
    id: "draft-1",
    upstreamRepo: "AquilaXk/aquila-buddy",
    githubIssueNumber: 261,
    sourceIssueId: "issue-1",
    baseBranch: "main",
    headBranch: "test/front-backend-integration-smoke",
    title: "[Test] 프론트 백엔드 통합 smoke 검증 추가",
    body: "## Related issue\nclose #261\n\n## Summary\n- 프론트엔드 브라우저 integration smoke 검증 추가\n- Signature Lavender 디자인 시스템 및 7대 기여 준비도 게이트 연동\n\n## Test Plan\n- npm --prefix front run smoke:integration\n- ./back/gradlew -p back check\n\n## Breaking Changes\n- None (100% backward compatible)",
    createdAt: "2026-05-18T12:00:00Z",
  };
}

function translationFixture() {
  return {
    id: "translation-1",
    draftId: "draft-1",
    sourceIssueId: "issue-1",
    targetLanguage: "en",
    title: "Add frontend backend integration smoke",
    body: "## Related issue\nclose #261\n\n## Summary\n- Add browser integration smoke verification\n- Connect Signature Lavender design system and 7-gate contribution readiness\n\n## Test Plan\n- npm --prefix front run smoke:integration\n- ./back/gradlew -p back check\n\n## Breaking Changes\n- None",
    createdAt: "2026-05-18T12:01:00Z",
  };
}

function readinessFixture(draftId, sourceIssueId) {
  return {
    draftId,
    sourceIssueId,
    readyCount: 7,
    items: [
      readinessItem("branch-flow", "Branch Flow", "대상 저장소, 이슈 번호, base/head branch 흐름", "AquilaXk/aquila-buddy #261 / main -> test/front-backend-integration-smoke", "READY"),
      readinessItem("issue-link", "Issue Link Syntax", "GitHub 이슈 연계 및 종료 구문", "이슈 연계 구문(close #261)이 본문에 포함되어 있습니다.", "READY"),
      readinessItem("template-compliance", "Template Compliance", "마크다운 섹션 및 템플릿 필수 항목 준수", "본문 section 및 템플릿 필수 항목이 준비되어 있습니다.", "READY"),
      readinessItem("test-plan", "Test Plan", "검증 방법 및 테스트 계획 명시", "테스트 계획(npm run smoke:integration)이 포함되어 있습니다.", "READY"),
      readinessItem("clean-diff", "Clean Diff", "충돌 마커 없이 정리된 diff 여부", "충돌 마커 없이 정리된 diff가 확인되었습니다.", "READY"),
      readinessItem("breaking-changes", "Breaking Changes Check", "하위 호환성 영향 명시 여부", "하위 호환성 영향 없음이 명시되었습니다.", "READY"),
      readinessItem("title-convention", "Title Convention", "Conventional Commits 제목 규격 준수", "Conventional Commit 규약([Test])을 준수합니다.", "READY"),
    ],
  };
}

function submissionFixture() {
  return {
    draftId: "draft-1",
    upstreamRepo: "AquilaXk/aquila-buddy",
    githubIssueNumber: 261,
    sourceIssueId: "issue-1",
    baseBranch: "main",
    headBranch: "test/front-backend-integration-smoke",
    items: [
      { key: "title", label: "제목", description: "PR 제목", value: "[Test] 프론트 백엔드 통합 smoke 검증 추가" },
      { key: "body", label: "본문", description: "PR 본문", value: "close #261" },
      { key: "branch", label: "브랜치", description: "작업 브랜치", value: "test/front-backend-integration-smoke" },
      { key: "test-plan", label: "검증", description: "검증 계획", value: "npm --prefix front run smoke:integration" },
    ],
  };
}

function timelineFixture() {
  return {
    draftId: "draft-1",
    sourceIssueId: "issue-1",
    items: [{
      key: "draft-created",
      label: "초안 생성",
      description: "PR 초안이 준비되었습니다.",
      detail: "fixture draft ready",
      status: "DONE",
      occurredAt: "2026-05-18T12:00:00Z",
    }],
  };
}

function workflowStep(key, label, detail, status) {
  return {
    key,
    label,
    description: `${label} 단계`,
    detail,
    status,
  };
}

function readinessItem(key, label, description, detail, status) {
  return {
    key,
    label,
    description,
    detail,
    status,
  };
}
