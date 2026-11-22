import { authStateChangedEvent, clearAuthState, getToken, markLoggedIn } from "./auth";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
const REFRESH_URL = `${API_URL}/api/v1/auth/refresh`;
const csrfCookieName = "aquila_buddy_csrf";
const csrfHeaderName = "X-Aquila-CSRF-Token";
const mutatingMethods = new Set(["POST", "PATCH", "PUT", "DELETE"]);
const USER_PROFILE_CACHE_TTL_MS = 2_000;
let refreshSessionPromise: Promise<boolean> | null = null;
let userProfileInFlight: Promise<UserProfile> | null = null;
let cachedUserProfile: { value: UserProfile; expiresAt: number } | null = null;

export function invalidateUserProfileCache(): void {
  userProfileInFlight = null;
  cachedUserProfile = null;
}

if (typeof window !== "undefined" && typeof window.addEventListener === "function") {
  window.addEventListener(authStateChangedEvent, invalidateUserProfileCache);
}

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

export class ApiError extends Error {
  readonly status: number;
  readonly isAuthError: boolean;

  constructor(message: string, status: number, isAuthError = status === 401) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.isAuthError = isAuthError;
  }
}

async function extractApiErrorMessage(response: Response, fallbackMessage: string): Promise<string> {
  try {
    const errPayload = (await response.json()) as Partial<ApiResponse<unknown>> | null;
    if (errPayload && typeof errPayload.message === "string" && errPayload.message.trim().length > 0) {
      return errPayload.message;
    }
  } catch {
    // 응답 본문이 JSON이 아니면 기본 에러 메시지를 반환한다.
  }

  return fallbackMessage;
}

async function unwrapApiResponse<T>(
  response: Response,
  fallbackMessage: string,
  envelopeFallbackMessage = fallbackMessage
): Promise<T> {
  if (!response.ok) {
    const errorMessage = await extractApiErrorMessage(response, fallbackMessage);
    throw new ApiError(errorMessage, response.status, response.status === 401);
  }

  const payload = (await response.json()) as ApiResponse<T>;
  if (!hasApiData(payload)) {
    throw new ApiError(payload.message ?? envelopeFallbackMessage, response.status, false);
  }

  return payload.data;
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
    invalidateUserProfileCache();
    clearAuthState();
  }

  return response;
}

export async function logout(): Promise<void> {
  invalidateUserProfileCache();
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
  bookmarked?: boolean;
};

export type RecommendedIssueHistory = RecommendedIssue & {
  isAnalyzed: boolean;
  analysisResultId: string | null;
};

export class StreamServerError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "StreamServerError";
  }
}

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
  const now = Date.now();
  if (cachedUserProfile !== null && cachedUserProfile.expiresAt > now) {
    return cachedUserProfile.value;
  }

  if (userProfileInFlight !== null) {
    return userProfileInFlight;
  }

  // ProtectedLayout, Navbar, 페이지 컴포넌트의 동시 호출을 단일 요청으로 합친다.
  userProfileInFlight = (async () => {
    const response = await apiFetch(`${API_URL}/api/v1/users/me`, {
      cache: "no-store"
    });

    const profile = await unwrapApiResponse<UserProfile>(
      response,
      "Failed to fetch user profile",
      "User profile response was not successful"
    );

    cachedUserProfile = {
      value: profile,
      expiresAt: Date.now() + USER_PROFILE_CACHE_TTL_MS
    };

    return profile;
  })().finally(() => {
    userProfileInFlight = null;
  });

  return userProfileInFlight;
}

export async function updateUserProfile(request: UpdateUserProfileRequest): Promise<UserProfile> {
  invalidateUserProfileCache();

  const response = await apiFetch(`${API_URL}/api/v1/users/me`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(request)
  });

  const updated = await unwrapApiResponse<UserProfile>(
    response,
    "Failed to update user profile",
    "User profile update response was not successful"
  );
  invalidateUserProfileCache();
  return updated;
}

export async function refreshUserProfileVector(): Promise<UserProfile> {
  invalidateUserProfileCache();

  const response = await apiFetch(`${API_URL}/api/v1/users/me/vector`, {
    method: "POST"
  });

  const refreshed = await unwrapApiResponse<UserProfile>(
    response,
    "Failed to refresh user profile vector",
    "User profile vector refresh response was not successful"
  );
  invalidateUserProfileCache();
  return refreshed;
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

  return unwrapApiResponse<StoredPullRequestDraft>(
    response,
    "Failed to create pull request draft",
    "Pull request draft response was not successful"
  );
}

export async function getPullRequestDraftHistory(page = 0, size = 5): Promise<PullRequestDraftHistoryPage> {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  const response = await apiFetch(`${API_URL}/api/v1/pr/history?${searchParams.toString()}`, {
    cache: "no-store"
  });

  return unwrapApiResponse<PullRequestDraftHistoryPage>(
    response,
    "Failed to fetch pull request draft history",
    "Pull request draft history response was not successful"
  );
}

export async function getPullRequestDraft(id: string): Promise<StoredPullRequestDraft> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
    cache: "no-store"
  });

  return unwrapApiResponse<StoredPullRequestDraft>(
    response,
    "Failed to fetch pull request draft",
    "Pull request draft detail response was not successful"
  );
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

  return unwrapApiResponse<StoredPullRequestDraft>(
    response,
    "Failed to update pull request draft",
    "Pull request draft update response was not successful"
  );
}

export async function deletePullRequestDraft(id: string): Promise<void> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new Error(await extractApiErrorMessage(response, "Failed to delete pull request draft"));
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

  return unwrapApiResponse<PullRequestDraftReadiness>(
    response,
    "Failed to fetch pull request draft readiness",
    "Pull request draft readiness response was not successful"
  );
}

export async function translatePullRequestDraft(id: string): Promise<PullRequestDraftTranslateResult> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translate`, {
    method: "POST"
  });

  return unwrapApiResponse<PullRequestDraftTranslateResult>(
    response,
    "Failed to translate pull request draft",
    "Pull request draft translate response was not successful"
  );
}

export async function getPullRequestDraftTranslations(id: string): Promise<PullRequestDraftTranslation[]> {
  const response = await apiFetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations`, {
    cache: "no-store"
  });

  return unwrapApiResponse<PullRequestDraftTranslation[]>(
    response,
    "Failed to fetch pull request draft translations",
    "Pull request draft translations response was not successful"
  );
}

export async function getRecommendedIssues(): Promise<RecommendedIssue[]> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/recommend`, {
    cache: "no-store"
  });

  return unwrapApiResponse<RecommendedIssue[]>(
    response,
    "Failed to fetch recommended issues",
    "Recommended issues response was not successful"
  );
}

export async function getIssueDetail(id: string): Promise<RecommendedIssue> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(id)}`, {
    cache: "no-store"
  });

  return unwrapApiResponse<RecommendedIssue>(
    response,
    "Failed to fetch issue detail",
    "Issue detail response was not successful"
  );
}

export async function crawlSearchIssues(query: string): Promise<RecommendedIssue[]> {
  const searchParams = new URLSearchParams({
    q: query
  });

  const response = await apiFetch(`${API_URL}/api/v1/issues/crawl/search?${searchParams.toString()}`, {
    method: "POST"
  });

  return unwrapApiResponse<RecommendedIssue[]>(
    response,
    "Failed to crawl issue search results",
    "Issue crawl search response was not successful"
  );
}

export async function getRecommendedIssueHistory(): Promise<RecommendedIssueHistory[]> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/recommend/history`, {
    cache: "no-store"
  });

  return unwrapApiResponse<RecommendedIssueHistory[]>(
    response,
    "Failed to fetch recommended issue history",
    "Recommended issue history response was not successful"
  );
}

export async function getIssueBookmarks(): Promise<RecommendedIssueHistory[]> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/bookmarks`, {
    cache: "no-store"
  });

  return unwrapApiResponse<RecommendedIssueHistory[]>(
    response,
    "Failed to fetch bookmarked issues",
    "Issue bookmarks response was not successful"
  );
}

export async function bookmarkIssue(issueId: string): Promise<void> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/bookmark`, {
    method: "POST"
  });

  if (!response.ok) {
    throw new ApiError(
      await extractApiErrorMessage(response, "Failed to bookmark issue"),
      response.status,
      response.status === 401
    );
  }

  const payload = (await response.json()) as ApiResponse<unknown>;
  if (!isApiSuccess(payload)) {
    throw new ApiError(payload.message ?? "Issue bookmark response was not successful", response.status, false);
  }
}

export async function unbookmarkIssue(issueId: string): Promise<void> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/bookmark`, {
    method: "DELETE"
  });

  if (!response.ok) {
    throw new ApiError(
      await extractApiErrorMessage(response, "Failed to remove issue bookmark"),
      response.status,
      response.status === 401
    );
  }

  const payload = (await response.json()) as ApiResponse<unknown>;
  if (!isApiSuccess(payload)) {
    throw new ApiError(payload.message ?? "Issue unbookmark response was not successful", response.status, false);
  }
}

export async function getIssueGuide(issueId: string): Promise<IssueGuide> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/guide`, {
    cache: "no-store"
  });

  return unwrapApiResponse<IssueGuide>(
    response,
    "Failed to fetch issue guide",
    "Issue guide response was not successful"
  );
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
    const err = new ApiError(
      await extractApiErrorMessage(response, `Failed to stream issue guide: ${response.status}`),
      response.status,
      response.status === 401
    );
    callbacks.onError?.(err);
    throw err;
  }

  if (!response.body) {
    const err = new Error("Streaming response body is null");
    callbacks.onError?.(err);
    throw err;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let currentEvent = "";
  let finalGuide: IssueGuide | null = null;

  const processLines = (lines: string[]) => {
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.startsWith("event:")) {
        currentEvent = trimmed.slice(6).trim();
      } else if (trimmed.startsWith("data:")) {
        const dataStr = trimmed.slice(5).trim();
        if (!dataStr) continue;
        if (currentEvent === "error") {
          let errorMessage = dataStr;
          try {
            const parsedError = JSON.parse(dataStr) as { message?: unknown };
            if (typeof parsedError?.message === "string" && parsedError.message.trim().length > 0) {
              errorMessage = parsedError.message;
            }
          } catch {
            // JSON이 아닌 오류 텍스트는 원문을 그대로 사용한다.
          }
          const streamError = new StreamServerError(errorMessage || "Stream error");
          callbacks.onError?.(streamError);
          throw streamError;
        }
        try {
          const parsed = JSON.parse(dataStr);
          if (currentEvent === "complete") {
            finalGuide = parsed as IssueGuide;
            callbacks.onComplete?.(finalGuide);
          } else {
            callbacks.onChunk?.(parsed as IssueGuideStreamEvent);
          }
        } catch {
          // JSON 형식이 아닌 keep-alive ping은 무시한다.
        }
      } else if (trimmed === "") {
        currentEvent = "";
      }
    }
  };

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        buffer += decoder.decode();
        if (buffer.trim().length > 0) {
          processLines(buffer.split("\n"));
        }
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";
      processLines(lines);
    }
  } finally {
    reader.releaseLock();
  }

  if (finalGuide) {
    return finalGuide;
  }

  if (signal?.aborted) {
    throw new DOMException("The operation was aborted.", "AbortError");
  }

  return await getIssueGuide(issueId);
}

export async function getIssuePseudoCode(issueId: string): Promise<IssuePseudoCode> {
  const response = await apiFetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/pseudo`, {
    cache: "no-store"
  });

  return unwrapApiResponse<IssuePseudoCode>(
    response,
    "Failed to fetch issue pseudo code",
    "Issue pseudo code response was not successful"
  );
}
