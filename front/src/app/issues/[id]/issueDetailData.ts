import {
  getIssueDetail,
  getIssueGuide,
  getIssuePseudoCode,
  getIssueGuideWorkflow,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  type IssueGuide,
  type IssuePseudoCode,
  type IssueGuideWorkflow,
  type RecommendedIssue,
  type RecommendedIssueHistory
} from "@/lib/api";

export type IssueDetailIssue = RecommendedIssue | RecommendedIssueHistory;

export type IssueDetailOverview = {
  guide: IssueGuide | null;
  pseudoCode: IssuePseudoCode | null;
  workflow: IssueGuideWorkflow | null;
  issue: IssueDetailIssue | null;
  draftHref: string | null;
  errors: {
    guide: boolean;
    pseudoCode: boolean;
    workflow: boolean;
    issue: boolean;
  };
};

type LoadResult<T> = {
  data: T;
  error: boolean;
};

type IssueCollectionResult = {
  issues: IssueDetailIssue[];
  error: boolean;
};

export async function loadIssueDetailOverview(issueId: string): Promise<IssueDetailOverview> {
  const [guideResult, pseudoCodeResult, workflowResult, issueResult] = await Promise.all([
    loadSafely(getIssueGuide(issueId), null),
    loadSafely(getIssuePseudoCode(issueId), null),
    loadSafely(getIssueGuideWorkflow(issueId), null),
    loadIssueById(issueId)
  ]);

  return {
    guide: guideResult.data,
    pseudoCode: pseudoCodeResult.data,
    workflow: workflowResult.data,
    issue: issueResult.data,
    draftHref: issueResult.data ? buildPullRequestDraftHref(issueResult.data, guideResult.data) : null,
    errors: {
      guide: guideResult.error,
      pseudoCode: pseudoCodeResult.error,
      workflow: workflowResult.error,
      issue: issueResult.error
    }
  };
}

export function buildPullRequestDraftHref(issue: IssueDetailIssue, guide: IssueGuide | null): string {
  const params = new URLSearchParams({
    repo: issue.repoFullName,
    issue: String(issue.issueNumber),
    base: "main",
    head: buildHeadBranch(issue),
    diff: buildDiffSeed(issue, guide),
    sourceIssueId: issue.id
  });

  return `/pr-draft?${params.toString()}`;
}

async function loadIssueById(issueId: string): Promise<LoadResult<IssueDetailIssue | null>> {
  try {
    return {
      data: await loadDirectIssue(issueId),
      error: false
    };
  } catch {
    return fallbackIssueLookup(issueId);
  }
}

async function loadDirectIssue(issueId: string): Promise<RecommendedIssue> {
  return await getIssueDetail(issueId);
}

async function fallbackIssueLookup(issueId: string): Promise<LoadResult<IssueDetailIssue | null>> {
  const collectionResult = await loadIssueCollections();
  const issue = collectionResult.issues.find((candidate) => candidate.id === issueId) ?? null;

  return {
    data: issue,
    error: collectionResult.error
  };
}

async function loadIssueCollections(): Promise<IssueCollectionResult> {
  const [historyResult, recommendationsResult] = await Promise.allSettled([
    getRecommendedIssueHistory(),
    getRecommendedIssues()
  ]);

  const history = historyResult.status === "fulfilled" ? historyResult.value : [];
  const recommendations = recommendationsResult.status === "fulfilled" ? recommendationsResult.value : [];

  return {
    issues: [...history, ...recommendations],
    error: historyResult.status === "rejected" && recommendationsResult.status === "rejected"
  };
}

function buildHeadBranch(issue: IssueDetailIssue): string {
  const slug = issue.title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40)
    .replace(/-+$/g, "");

  return `feat/${slug || `issue-${issue.issueNumber}`}`;
}

function buildDiffSeed(issue: IssueDetailIssue, guide: IssueGuide | null): string {
  const guideSummary = guide?.guideline ?? issue.summary;

  return [
    `diff --git a/ISSUE-${issue.issueNumber}.md b/ISSUE-${issue.issueNumber}.md`,
    `+${issue.title}`,
    `+${guideSummary}`
  ].join("\n");
}

async function loadSafely<T>(promise: Promise<T>, fallback: T): Promise<LoadResult<T>> {
  try {
    return {
      data: await promise,
      error: false
    };
  } catch {
    return {
      data: fallback,
      error: true
    };
  }
}
