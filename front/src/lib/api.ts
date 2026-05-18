const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

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

export type UserProfile = {
  githubId: number;
  login: string;
  name: string;
  email: string;
  primaryLanguages: string[];
  topics: string[];
  profileReady: boolean;
  vectorUpdatedAt: string;
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

export type RecommendedIssueHistory = Omit<RecommendedIssue, "reason"> & {
  isAnalyzed: boolean;
  analysisResultId: string | null;
};

export type IssueGuide = {
  issueKey: string;
  guideline: string;
  pseudoCode: string;
  sideEffects: string[];
};

export type IssuePseudoCode = {
  issueKey: string;
  pseudoCode: string;
};

export type IssueGuideWorkflowStatus = "READY" | "WAITING";

export type IssueGuideWorkflowStep = {
  key: string;
  label: string;
  description: string;
  detail: string;
  status: IssueGuideWorkflowStatus;
};

export type IssueGuideWorkflow = {
  issueKey: string;
  steps: IssueGuideWorkflowStep[];
  readyCount: number;
};

export type CreatePullRequestDraftRequest = {
  upstreamRepo: string;
  githubIssueNumber: number;
  sourceIssueId?: string;
  baseBranch: string;
  headBranch: string;
  diffContent: string;
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
  githubIssueNumber: number;
  sourceIssueId: string | null;
  baseBranch: string;
  headBranch: string;
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

export type PullRequestDraftSubmissionItem = {
  key: "title" | "body" | "branch" | "test-plan";
  label: string;
  description: string;
  value: string;
};

export type PullRequestDraftSubmission = {
  draftId: string;
  upstreamRepo: string;
  githubIssueNumber: number;
  sourceIssueId: string | null;
  baseBranch: string;
  headBranch: string;
  items: PullRequestDraftSubmissionItem[];
};

export type PullRequestDraftContributionTranslation = {
  translationId: string;
  targetLanguage: "en";
  title: string;
  createdAt: string;
};

export type PullRequestDraftContributionReport = {
  draftId: string;
  upstreamRepo: string;
  githubIssueNumber: number;
  sourceIssueId: string | null;
  baseBranch: string;
  headBranch: string;
  title: string;
  createdAt: string;
  readinessReadyCount: number;
  readinessTotalCount: number;
  translationCount: number;
  latestTranslation: PullRequestDraftContributionTranslation | null;
};

export type PullRequestDraftTimelineStatus = "DONE" | "READY" | "NEEDS_ATTENTION";

export type PullRequestDraftTimelineItem = {
  key: "context-captured" | "draft-created" | "readiness-reviewed" | "submission-prepared";
  label: string;
  description: string;
  detail: string;
  status: PullRequestDraftTimelineStatus;
  occurredAt: string;
};

export type PullRequestDraftTimeline = {
  draftId: string;
  sourceIssueId: string | null;
  items: PullRequestDraftTimelineItem[];
};

export type PullRequestDraftTranslation = PullRequestDraft & {
  id: string;
  draftId: string;
  targetLanguage: "en";
  createdAt: string;
};

export type PullRequestDraftTranslateResult = {
  titleEn: string;
  bodyEn: string;
  sourceIssueId: string | null;
  githubUrl: string;
};

export type PullRequestDraftTranslationActivity = {
  draftId: string;
  translationId: string;
  upstreamRepo: string;
  githubIssueNumber: number;
  sourceIssueId: string | null;
  targetLanguage: "en";
  title: string;
  createdAt: string;
};

export type PullRequestDraftTranslationReadiness = {
  draftId: string;
  translationId: string;
  targetLanguage: "en";
  sourceIssueId: string | null;
  items: PullRequestDraftReadinessItem[];
  readyCount: number;
};

export type PullRequestDraftTranslationTimelineItem = {
  key: "translation-created" | "translation-ready" | "readiness-reviewed" | "submission-prepared";
  label: string;
  description: string;
  detail: string;
  status: PullRequestDraftTimelineStatus;
  occurredAt: string;
};

export type PullRequestDraftTranslationTimeline = {
  draftId: string;
  translationId: string;
  targetLanguage: "en";
  sourceIssueId: string | null;
  items: PullRequestDraftTranslationTimelineItem[];
};

export type PullRequestDraftTranslationSubmission = {
  draftId: string;
  translationId: string;
  targetLanguage: "en";
  upstreamRepo: string;
  githubIssueNumber: number;
  sourceIssueId: string | null;
  baseBranch: string;
  headBranch: string;
  items: PullRequestDraftSubmissionItem[];
};

export async function getUserProfile(): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/me`, {
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

export async function getUserProfileByLogin(githubId: string): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/${encodeURIComponent(githubId)}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch public user profile");
  }

  const payload = (await response.json()) as ApiResponse<UserProfile>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Public user profile response was not successful");
  }

  return payload.data;
}

export async function updateUserProfile(request: UpdateUserProfileRequest): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/me`, {
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
  const response = await fetch(`${API_URL}/api/v1/users/me/vector`, {
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
  const response = await fetch(`${API_URL}/api/v1/pr`, {
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

export async function getPullRequestDrafts(): Promise<StoredPullRequestDraft[]> {
  const response = await fetch(`${API_URL}/api/v1/pr`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request drafts");
  }

  const payload = (await response.json()) as ApiResponse<StoredPullRequestDraft[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request drafts response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftHistory(page = 0, size = 5): Promise<PullRequestDraftHistoryPage> {
  const searchParams = new URLSearchParams({
    page: String(page),
    size: String(size)
  });

  const response = await fetch(`${API_URL}/api/v1/pr/history?${searchParams.toString()}`, {
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
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
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
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
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
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}`, {
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
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/readiness`, {
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

export async function getPullRequestDraftSubmission(id: string): Promise<PullRequestDraftSubmission> {
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/submission`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft submission bundle");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftSubmission>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft submission response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftTimeline(id: string): Promise<PullRequestDraftTimeline> {
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/timeline`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft timeline");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTimeline>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft timeline response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftContributionReport(
  id: string
): Promise<PullRequestDraftContributionReport> {
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/report`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft contribution report");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftContributionReport>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft contribution report response was not successful");
  }

  return payload.data;
}

export async function createPullRequestDraftTranslation(
  id: string,
  targetLanguage: "en"
): Promise<PullRequestDraftTranslation> {
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ targetLanguage })
  });

  if (!response.ok) {
    throw new Error("Failed to create pull request draft translation");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslation>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translation response was not successful");
  }

  return payload.data;
}

export async function translatePullRequestDraft(id: string): Promise<PullRequestDraftTranslateResult> {
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translate`, {
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
  const response = await fetch(`${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations`, {
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

export async function getPullRequestDraftTranslationActivities(
  limit = 4
): Promise<PullRequestDraftTranslationActivity[]> {
  const searchParams = new URLSearchParams({
    limit: String(limit)
  });

  const response = await fetch(`${API_URL}/api/v1/pr/translations/activity?${searchParams.toString()}`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft translation activities");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslationActivity[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translation activities response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftTranslationReadiness(
  id: string,
  translationId: string
): Promise<PullRequestDraftTranslationReadiness> {
  const response = await fetch(
    `${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations/${encodeURIComponent(translationId)}/readiness`,
    {
      cache: "no-store"
    }
  );

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft translation readiness");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslationReadiness>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translation readiness response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftTranslationTimeline(
  id: string,
  translationId: string
): Promise<PullRequestDraftTranslationTimeline> {
  const response = await fetch(
    `${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations/${encodeURIComponent(translationId)}/timeline`,
    {
      cache: "no-store"
    }
  );

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft translation timeline");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslationTimeline>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translation timeline response was not successful");
  }

  return payload.data;
}

export async function getPullRequestDraftTranslationSubmission(
  id: string,
  translationId: string
): Promise<PullRequestDraftTranslationSubmission> {
  const response = await fetch(
    `${API_URL}/api/v1/pr/${encodeURIComponent(id)}/translations/${encodeURIComponent(translationId)}/submission`,
    {
      cache: "no-store"
    }
  );

  if (!response.ok) {
    throw new Error("Failed to fetch pull request draft translation submission bundle");
  }

  const payload = (await response.json()) as ApiResponse<PullRequestDraftTranslationSubmission>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Pull request draft translation submission response was not successful");
  }

  return payload.data;
}

export async function getRecommendedIssues(): Promise<RecommendedIssue[]> {
  const response = await fetch(`${API_URL}/api/v1/issues/recommend`, {
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

export async function getCollectedIssues(): Promise<RecommendedIssue[]> {
  const response = await fetch(`${API_URL}/api/v1/issues`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch collected issues");
  }

  const payload = (await response.json()) as ApiResponse<RecommendedIssue[]>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Collected issues response was not successful");
  }

  return payload.data;
}

export async function getIssueDetail(id: string): Promise<RecommendedIssue> {
  const response = await fetch(`${API_URL}/api/v1/issues/${encodeURIComponent(id)}`, {
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

  const response = await fetch(`${API_URL}/api/v1/issues/crawl/search?${searchParams.toString()}`, {
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
  const response = await fetch(`${API_URL}/api/v1/issues/recommend/history`, {
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
  const response = await fetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/guide`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch issue guide");
  }

  const payload = (await response.json()) as ApiResponse<IssueGuide>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Issue guide response was not successful");
  }

  return payload.data;
}

export async function getIssuePseudoCode(issueId: string): Promise<IssuePseudoCode> {
  const response = await fetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/pseudo`, {
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

export async function getIssueGuideWorkflow(issueId: string): Promise<IssueGuideWorkflow> {
  const response = await fetch(`${API_URL}/api/v1/issues/${encodeURIComponent(issueId)}/guide/workflow`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch issue guide workflow");
  }

  const payload = (await response.json()) as ApiResponse<IssueGuideWorkflow>;

  if (!hasApiData(payload)) {
    throw new Error(payload.message ?? "Issue guide workflow response was not successful");
  }

  return payload.data;
}
