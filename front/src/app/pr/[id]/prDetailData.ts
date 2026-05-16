import {
  getPullRequestDraft,
  getPullRequestDraftReadiness,
  getPullRequestDraftSubmission,
  getPullRequestDraftTimeline,
  type PullRequestDraftReadiness,
  type PullRequestDraftSubmission,
  type PullRequestDraftTimeline,
  type StoredPullRequestDraft
} from "@/lib/api";

export type PrDetailOverview = {
  draft: StoredPullRequestDraft | null;
  readiness: PullRequestDraftReadiness | null;
  submission: PullRequestDraftSubmission | null;
  timeline: PullRequestDraftTimeline | null;
  errors: {
    draft: boolean;
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
  const [draftResult, readinessResult, submissionResult, timelineResult] = await Promise.all([
    loadSafely(getPullRequestDraft(id), null),
    loadSafely(getPullRequestDraftReadiness(id), null),
    loadSafely(getPullRequestDraftSubmission(id), null),
    loadSafely(getPullRequestDraftTimeline(id), null)
  ]);

  return {
    draft: draftResult.data,
    readiness: readinessResult.data,
    submission: submissionResult.data,
    timeline: timelineResult.data,
    errors: {
      draft: draftResult.error,
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
