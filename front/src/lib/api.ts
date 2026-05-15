const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

type ApiResponse<T> = {
  success: boolean;
  data: T | null;
  message: string | null;
};

export type HealthResponse = {
  status: string;
  service: string;
};

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

export type CreatePullRequestDraftRequest = {
  upstreamRepo: string;
  githubIssueNumber: number;
  baseBranch: string;
  headBranch: string;
  diffContent: string;
};

export type PullRequestDraft = {
  title: string;
  body: string;
};

export type StoredPullRequestDraft = PullRequestDraft & {
  id: string;
  upstreamRepo: string;
  githubIssueNumber: number;
  baseBranch: string;
  headBranch: string;
  createdAt: string;
};

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_URL}/api/v1/health`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch backend health");
  }

  return response.json() as Promise<HealthResponse>;
}

export async function getUserProfile(): Promise<UserProfile> {
  const response = await fetch(`${API_URL}/api/v1/users/me`, {
    cache: "no-store"
  });

  if (!response.ok) {
    throw new Error("Failed to fetch user profile");
  }

  const payload = (await response.json()) as ApiResponse<UserProfile>;

  if (!payload.success || !payload.data) {
    throw new Error(payload.message ?? "User profile response was not successful");
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

  if (!payload.success || !payload.data) {
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

  if (!payload.success || !payload.data) {
    throw new Error(payload.message ?? "Pull request drafts response was not successful");
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

  if (!payload.success || !payload.data) {
    throw new Error(payload.message ?? "Pull request draft detail response was not successful");
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

  if (!payload.success || !payload.data) {
    throw new Error(payload.message ?? "Recommended issues response was not successful");
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

  if (!payload.success || !payload.data) {
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

  if (!payload.success || !payload.data) {
    throw new Error(payload.message ?? "Issue guide response was not successful");
  }

  return payload.data;
}
