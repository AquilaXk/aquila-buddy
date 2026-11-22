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

    const payload = routeFixture(url, request.method ?? "GET", state);
    if (payload.rawSse) {
      writeSse(response, payload.rawSse, origin);
      return;
    }
    writeJson(response, payload.httpStatus ?? 200, payload.body, origin, payload.extraHeaders);
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

  if (pathname === "/api/v1/auth/refresh" && method === "POST") {
    return response(successEnvelope({ refreshed: true }), 200, {
      "Set-Cookie": "aquila_buddy_csrf=smoke-csrf; Path=/; SameSite=Lax",
    });
  }

  if (pathname === "/api/v1/auth/logout" && method === "POST") {
    return response(successEnvelope(null), 200, {
      "Set-Cookie": "aquila_buddy_csrf=; Path=/; Max-Age=0",
    });
  }

  if (pathname === "/api/v1/users/me") {
    return response(successEnvelope(profileFixture()));
  }

  if (pathname === "/api/v1/users/me/vector") {
    return response(successEnvelope({ ...profileFixture(), profileReady: true }));
  }

  if (pathname === "/api/v1/issues/recommend/history" || pathname === "/api/v1/issues/recommend") {
    return response(successEnvelope([issueHistoryFixture()]));
  }

  if (pathname === "/api/v1/issues/bookmarks") {
    return response(successEnvelope([]));
  }

  if (pathname.startsWith("/api/v1/issues/") && pathname.endsWith("/bookmark")) {
    return response(successEnvelope(null));
  }

  if (pathname === "/api/v1/issues" || pathname === "/api/v1/issues/crawl/search") {
    return response(successEnvelope([issueFixture()]));
  }

  if (pathname === "/api/v1/issues/issue-1") {
    return response(successEnvelope(issueFixture()));
  }

  if (pathname === "/api/v1/issues/issue-1/guide") {
    return response(successEnvelope({
      issueKey: "aquila-buddy#261",
      filePaths: ["front/src/app/issues/[id]/page.tsx"],
      guideline: "fixture backend와 연결된 화면 smoke를 추가합니다.",
      pseudoCode: "start fixture backend\nvisit protected routes\nassert envelope rendering",
      sideEffects: ["CI runtime이 늘어납니다."],
    }));
  }

  if (pathname === "/api/v1/issues/issue-1/guide/stream") {
    return {
      rawSse: [
        "event: chunk\ndata: " + JSON.stringify({ step: "ANALYZING", message: "저장소 코드 맥락 분석 중..." }) + "\n\n",
        "event: chunk\ndata: " + JSON.stringify({ step: "FILES", filePaths: ["front/src/app/issues/[id]/page.tsx"] }) + "\n\n",
        "event: chunk\ndata: " + JSON.stringify({ step: "GUIDELINE", chunk: "fixture backend와 연결된 화면 smoke를 추가합니다." }) + "\n\n",
        "event: complete\ndata: " + JSON.stringify({
          issueKey: "aquila-buddy#261",
          filePaths: ["front/src/app/issues/[id]/page.tsx"],
          guideline: "fixture backend와 연결된 화면 smoke를 추가합니다.",
          pseudoCode: "start fixture backend\nvisit protected routes\nassert envelope rendering",
          sideEffects: ["CI runtime이 늘어납니다."]
        }) + "\n\n",
      ],
    };
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

function writeSse(response, chunks, origin) {
  const headers = {
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Aquila-CSRF-Token",
    "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
    "Access-Control-Allow-Origin": origin ?? "*",
    "Cache-Control": "no-cache, no-transform",
    "Connection": "keep-alive",
    "Content-Type": "text/event-stream; charset=utf-8",
  };
  if (origin) {
    headers["Access-Control-Allow-Credentials"] = "true";
  }
  response.writeHead(200, headers);
  for (const chunk of chunks) {
    response.write(chunk);
  }
  response.end();
}

function writeJson(response, status, body, origin, extraHeaders = {}) {
  const headers = {
    "Access-Control-Allow-Headers": "Content-Type, Authorization, X-Aquila-CSRF-Token",
    "Access-Control-Allow-Methods": "GET, POST, PATCH, DELETE, OPTIONS",
    "Access-Control-Allow-Origin": origin ?? "*",
    "Cache-Control": "no-store",
    "Content-Type": "application/json; charset=utf-8",
    ...extraHeaders,
  };

  if (origin) {
    headers["Access-Control-Allow-Credentials"] = "true";
  }

  response.writeHead(status, headers);
  response.end(body === null ? "" : JSON.stringify(body));
}

function response(body, httpStatus = 200, extraHeaders = {}) {
  return { body, httpStatus, extraHeaders };
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
    score: 0.91,
    labels: ["test", "frontend"],
    status: "OPEN",
    reason: "프론트와 API envelope 통합 검증이 필요합니다.",
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
    body: "## 관련 이슈\nclose #261\n\n## 요약\n브라우저 smoke를 추가합니다.",
    diffContent: "diff --git a/front/src/app/pr-draft/page.tsx b/front/src/app/pr-draft/page.tsx\n--- a/front/src/app/pr-draft/page.tsx\n+++ b/front/src/app/pr-draft/page.tsx\n@@ -1,2 +1,3 @@\n import Link from \"next/link\";\n+import { DiffViewer } from \"@/components/DiffViewer\";\n export default function Page() {}",
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
    body: "## Related issue\nclose #261\n\n## Summary\nAdd browser smoke.",
    createdAt: "2026-05-18T12:01:00Z",
  };
}

function readinessFixture(draftId, sourceIssueId) {
  return {
    draftId,
    sourceIssueId,
    readyCount: 2,
    items: [
      readinessItem("title", "제목", "PR 제목 확인", "제목이 있습니다.", "READY"),
      readinessItem("body", "본문", "PR 본문 확인", "close #261이 있습니다.", "READY"),
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
