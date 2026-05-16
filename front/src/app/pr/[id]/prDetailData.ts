import {
  getPullRequestDraft,
  getPullRequestDraftContributionReport,
  getPullRequestDraftReadiness,
  getPullRequestDraftSubmission,
  getPullRequestDraftTimeline,
  type PullRequestDraftContributionReport,
  type PullRequestDraftReadiness,
  type PullRequestDraftSubmission,
  type PullRequestDraftTimeline,
  type StoredPullRequestDraft
} from "@/lib/api";

export type PrDetailOverview = {
  draft: StoredPullRequestDraft | null;
  report: PullRequestDraftContributionReport | null;
  readiness: PullRequestDraftReadiness | null;
  submission: PullRequestDraftSubmission | null;
  timeline: PullRequestDraftTimeline | null;
  errors: {
    draft: boolean;
    report: boolean;
    readiness: boolean;
    submission: boolean;
    timeline: boolean;
  };
};

type LoadResult<T> = {
  data: T;
  error: boolean;
};

export async function loadPrDetailOverview(id: string): Promise<PrDetailOverview> {
  const [draftResult, reportResult, readinessResult, submissionResult, timelineResult] = await Promise.all([
    loadSafely(getPullRequestDraft(id), null),
    loadSafely(getPullRequestDraftContributionReport(id), null),
    loadSafely(getPullRequestDraftReadiness(id), null),
    loadSafely(getPullRequestDraftSubmission(id), null),
    loadSafely(getPullRequestDraftTimeline(id), null)
  ]);

  return {
    draft: draftResult.data,
    report: reportResult.data,
    readiness: readinessResult.data,
    submission: submissionResult.data,
    timeline: timelineResult.data,
    errors: {
      draft: draftResult.error,
      report: reportResult.error,
      readiness: readinessResult.error,
      submission: submissionResult.error,
      timeline: timelineResult.error
    }
  };
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
