import { clearAuthState, getToken, markLoggedIn } from "./auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const REFRESH_URL = `${API_URL}/api/v1/auth/refresh`;
const csrfCookieName = "aquila_buddy_csrf";
const csrfHeaderName = "X-Aquila-CSRF-Token";
const mutatingMethods = new Set(["POST", "PATCH", "PUT", "DELETE"]);
let refreshSessionPromise: Promise<boolean> | null = null;

type ApiResponse<T> = {
  success?: boolean;
  status?: "success" | "fail" | string;
  data: T | null;
  message: string | null;
};

function isApiSuccess<T>(payload: ApiResponse<T>) {
  return payload.success === true || payload.status === "success";
}

function hasApiData<T>(payload: ApiResponse<T>): payload is ApiResponse<T> & { data: T } {
  return isApiSuccess(payload) && payload.data !== null && payload.data !== undefined;
}

type ApiFetchInit = RequestInit & {
  skipAuthRefresh?: boolean;
};

function readCookieValue(name: string) {
  if (typeof document === "undefined") {
    return null;
  }

  const prefix = `${name}=`;
  return document.cookie
    .split(";")
    .map((cookie) => cookie.trim())
    .find((cookie) => cookie.startsWith(prefix))
    ?.slice(prefix.length) ?? null;
}

function resolveRequestMethod(input: RequestInfo | URL, init: RequestInit) {
  if (init.method) {
    return init.method.toUpperCase();
  }

  if (typeof Request !== "undefined" && input instanceof Request) {
    return input.method.toUpperCase();
  }

  return "GET";
}

function buildApiHeaders(headers?: HeadersInit, method = "GET") {
  const nextHeaders = new Headers(headers);
  const token = getToken();

  if (token !== null) {
    nextHeaders.set("Authorization", `Bearer ${token}`);
  }

  if (mutatingMethods.has(method) && !nextHeaders.has(csrfHeaderName)) {
    const csrfToken = readCookieValue(csrfCookieName);
    if (csrfToken !== null) {
      nextHeaders.set(csrfHeaderName, csrfToken);
    }
  }

  return nextHeaders;
}

function requestUrl(input: RequestInfo | URL) {
  if (typeof input === "string") {
    return input;
  }
  if (input instanceof URL) {
    return input.toString();
  }
  return input.url;
}

function isAuthSessionEndpoint(input: RequestInfo | URL) {
  const url = requestUrl(input);
  return url.endsWith("/api/v1/auth/refresh") || url.endsWith("/api/v1/auth/logout");
}

function shouldAttemptRefresh(input: RequestInfo | URL, init: ApiFetchInit) {
  return init.skipAuthRefresh !== true && !isAuthSessionEndpoint(input);
}

function shouldBootstrapCsrf(input: RequestInfo | URL, init: ApiFetchInit, method: string) {
  if (init.skipAuthRefresh === true || !mutatingMethods.has(method) || isAuthSessionEndpoint(input)) {
    return false;
  }

  const headers = new Headers(init.headers);
  if (headers.has(csrfHeaderName)) {
    return false;
  }

  return readCookieValue(csrfCookieName) === null;
}

async function refreshSession() {
  const response = await fetch(REFRESH_URL, {
    method: "POST",
    credentials: "include",
    headers: buildApiHeaders(undefined, "POST")
  });

  if (response.ok) {
    markLoggedIn();
    return true;
  }

  return false;
}

async function refreshSessionOnce() {
  if (refreshSessionPromise === null) {
    // 만료된 세션에서 병렬 API 요청이 refresh 토큰을 동시에 회전시키지 않도록 공유한다.
    refreshSessionPromise = refreshSession().finally(() => {
      refreshSessionPromise = null;
    });
  }

  return refreshSessionPromise;
}

async function apiFetch(input: RequestInfo | URL, init: ApiFetchInit = {}) {
  const { skipAuthRefresh, ...fetchInit } = init;
  const method = resolveRequestMethod(input, fetchInit);

  if (shouldBootstrapCsrf(input, init, method)) {
    await refreshSessionOnce();
  }

  const response = await fetch(input, {
    ...fetchInit,
    credentials: fetchInit.credentials ?? "include",
    headers: buildApiHeaders(fetchInit.headers, method)
  });

  if (response.status === 401 && shouldAttemptRefresh(input, init) && await refreshSessionOnce()) {
    return fetch(input, {
      ...fetchInit,
      credentials: fetchInit.credentials ?? "include",
      headers: buildApiHeaders(fetchInit.headers, method)
    });
  }

  if (response.status === 401) {
    clearAuthState();
  }

  return response;
}

export async function logout(): Promise<void> {
  await apiFetch(`${API_URL}/api/v1/auth/logout`, {
    method: "POST"
  });
}

export type UserProfile = {
  id: string;
  githubId: number;
  login: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  primaryLanguages: string[];
  topics: string[];
  profileReady: boolean;
  vectorUpdatedAt: string | null;
};

export type UpdateUserProfileRequest = {
  name: string;
  email: string;
  primaryLanguages: string[];
  topics: string[];
};

export type RecommendedIssue = {
  id: string;
  repoFullName: string;
  issueNumber: number;
  title: string;
  summary: string;
  score: number;
  labels: string[];
  status: "OPEN" | "CLOSED";
  reason: string;
};

export type RecommendedIssueHistory = RecommendedIssue & {
  isAnalyzed: boolean;
  analysisResultId: string | null;
};

export type IssueGuide = {
  issueKey: string;
  filePaths: string[];
  guideline: string;
  pseudoCode: string;
  sideEffects: string[];
};

export type IssueGuideStreamEvent = {
  step?: "ANALYZING" | "FILES" | "GUIDELINE" | "PSEUDO_CODE" | "SIDE_EFFECTS";
  message?: string;
  chunk?: string;
  filePaths?: string[];
  sideEffects?: string[];
};

export type IssuePseudoCode = {
  issueKey: string;
  filePaths: string[];
  pseudoCode: string;
};

export type CreatePullRequestDraftRequest = {
  upstreamRepo: string;
  githubIssueNumber: number;
  sourceIssueId?: string;
  baseBranch: string;
  headBranch: string;
  diffContent?: string;
};

export type UpdatePullRequestDraftRequest = {
  title: string;
  body: string;
};

export type PullRequestDraft = {
  title: string;
  body: string;
};

export type StoredPullRequestDraft = PullRequestDraft & {
  id: string;
  upstreamRepo: string;
  repoFullName: string;
  githubIssueNumber: number;
  issueTitle: string;
  sourceIssueId: string | null;
  baseBranch: string;
  headBranch: string;
  diffContent: string;
  createdAt: string;
};

export type PullRequestDraftHistoryPage = {
  content: StoredPullRequestDraft[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
};

export type PullRequestDraftReadinessStatus = "READY" | "ACTION_REQUIRED";

export type PullRequestDraftReadinessItem = {
  key: string;
  label: string;
  description: string;
  detail: string;
  status: PullRequestDraftReadinessStatus;
};

export type PullRequestDraftReadiness = {
  draftId: string;
  sourceIssueId: string | null;
  items: PullRequestDraftReadinessItem[];
  readyCount: number;
};

export type PullRequestDraftTranslation = PullRequestDraft & {
  id: string;
  draftId: string;
  sourceIssueId: string | null;
  targetLanguage: "en";
  createdAt: string;
};

export type PullRequestDraftTranslateResult = {
  titleEn: string;
  bodyEn: string;
  sourceIssueId: string | null;
  githubUrl: string;
};

export async function getUserProfile(): Promise<UserProfile> {
  const response = await apiFetch(`${API_URL}/api/v1/users/me`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch user profile");
  }

  const payload = (await response.json()) as ApiResponse<UserProfile>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "User profile response was not successful");
  }

  return payload.data;
}

export async function updateUserProfile(request: UpdateUserProfileRequest): Promise<UserProfile> {
  const response = await apiFetch(`${API_URL}/api/v1/users/me`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    throw new Error("Failed to update user profile");
  }

  const payload = (await response.json()) as ApiResponse<UserProfile>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "User profile update response was not successful");
  }

  return payload.data;
}

export async function refreshUserProfileVector(): Promise<UserProfile> {
  const response = await apiFetch(`${API_URL}/api/v1/users/me/vector`, {
    method: "POST"
  });

  if (!response.ok) {
    throw new Error("Failed to refresh user profile vector");
  }

  const payload = (await response.json()) as ApiResponse<UserProfile>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "User profile vector refresh response was not successful");
  }

  return payload.data;
}

export async function createPullRequestDraft(
  request: CreatePullRequestDraftRequest
): Promise<StoredPullRequestDraft> {
  const response = await apiFetch(`${API_URL}/api/v1/pr`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    throw new Error("Failed to create pull request draft");
  }

  const payload = (await response.json()) as ApiResponse<StoredPullRequestDraft>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftHistory(page = 0, size = 5): Promise<PullRequestDraftHistoryPage> {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  const response = await apiFetch(`${API_URL}/api/v1/pr/history?${searchParams.toString()}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft history");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftHistoryPage>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft history response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraft(id: string): Promise<StoredPullRequestDraft> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft");
  }

  const payload = (await response.json()) as ApiResponse<StoredPullRequestDraft>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft detail response was not successful");
  }

  return payload.data;
}

export async function updatePullRequestDraft(
  id: string,
  request: UpdatePullRequestDraftRequest
): Promise<StoredPullRequestDraft> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  if (!response.ok) {
    throw new Error("Failed to update pull request draft");
  }

  const payload = (await response.json()) as ApiResponse<StoredPullRequestDraft>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft update response was not successful");
  }

  return payload.data;
}

export async function deletePullRequestDraft(id: string): Promise<void> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new Error("Failed to delete pull request draft");
  }

  const payload = (await response.json()) as ApiResponse<null>;

  if (!isApiSuccess(payload)) {
    throw new Error(payload.message ?? "Pull request draft delete response was not successful");
  }
}

export async function getPullRequestDraftReadiness(id: string): Promise<PullRequestDraftReadiness> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/readiness`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft readiness");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftReadiness>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft readiness response was not successful");
  }

  return payload.data;
}

export async function translatePullRequestDraft(id: string): Promise<PullRequestDraftTranslateResult> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translate`, {
    method: "POST"
  });

  if (!response.ok) {
    throw new Error("Failed to translate pull request draft");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslateResult>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translate response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftTranslations(id: string): Promise<PullRequestDraftTranslation[]> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft translations");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslation[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translations response was not successful");
  }

  return payload.data;
}

export async function getRecommendedIssues(): Promise<RecommendedIssue[]> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/recommend`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch recommended issues");
  }

  const payload = (await response.json()) as ApiResponse<RecommendedIssue[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Recommended issues response was not successful");
  }

  return payload.data;
}

export async function getIssueDetail(id: string): Promise<RecommendedIssue> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(id)}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch issue detail");
  }

  const payload = (await response.json()) as ApiResponse<RecommendedIssue>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Issue detail response was not successful");
  }

  return payload.data;
}

export async function crawlSearchIssues(query: string): Promise<RecommendedIssue[]> {
  const searchParams = new URLSearchParams({
    q: query
  });

  const response = await apiFetch(`${API_URL}/api/v1/issues/crawl/search?${searchParams.toString()}`, {
    method: "POST"
  });

  if (!response.ok) {
    throw new Error("Failed to crawl issue search results");
  }

  const payload = (await response.json()) as ApiResponse<RecommendedIssue[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Issue crawl search response was not successful");
  }

  return payload.data;
}

export async function getRecommendedIssueHistory(): Promise<RecommendedIssueHistory[]> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/recommend/history`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch recommended issue history");
  }

  const payload = (await response.json()) as ApiResponse<RecommendedIssueHistory[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Recommended issue history response was not successful");
  }

  return payload.data;
}

export async function getIssueGuide(issueId: string): Promise<IssueGuide> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/guide`, {
    cache: "no-store"
  });

  if (!response.ok) {
    let errorMessage = "Failed to fetch issue guide";
    try {
      const errPayload = await response.json();
      if (errPayload && typeof errPayload.message === "string") {
        errorMessage = errPayload.message;
      }
    } catch {
      // ignore json parse error
    }
    throw new Error(errorMessage);
  }

  const payload = (await response.json()) as ApiResponse<IssueGuide>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Issue guide response was not successful");
  }

  return payload.data;
}

export async function streamIssueGuide(
  issueId: string,
  callbacks: {
    onChunk?: (event: IssueGuideStreamEvent) => void;
    onComplete?: (guide: IssueGuide) => void;
    onError?: (error: Error) => void;
  } = {},
  signal?: AbortSignal
): Promise<IssueGuide> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/guide/stream`, {
    headers: {
      Accept: "text/event-stream"
    },
    signal,
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error(`Failed to stream issue guide: ${response.status}`);
  }

  if (!response.body) {
    throw new Error("Streaming response body is null");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let finalGuide: IssueGuide | null = null;

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() ?? "";

    let currentEvent = "";
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.startsWith("event:")) {
        currentEvent = trimmed.slice(6).trim();
      } else if (trimmed.startsWith("data:")) {
        const dataStr = trimmed.slice(5).trim();
        if (!dataStr) continue;
        try {
          const parsed = JSON.parse(dataStr);
          if (currentEvent === "complete") {
            finalGuide = parsed as IssueGuide;
            callbacks.onComplete?.(finalGuide);
          } else if (currentEvent === "error") {
            const streamError = new Error(parsed.message ?? "Stream error");
            callbacks.onError?.(streamError);
            throw streamError;
          } else {
            callbacks.onChunk?.(parsed as IssueGuideStreamEvent);
          }
        } catch (e) {
          if (currentEvent === "error") {
            throw e;
          }
          // ignore non-json ping
        }
      } else if (trimmed === "") {
        currentEvent = "";
      }
    }
  }

  if (finalGuide) {
    return finalGuide;
  }

  return await getIssueGuide(issueId);
}

export async function getIssuePseudoCode(issueId: string): Promise<IssuePseudoCode> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/pseudo`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch issue pseudo code");
  }

  const payload = (await response.json()) as ApiResponse<IssuePseudoCode>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Issue pseudo code response was not successful");
  }

  return payload.data;
}
