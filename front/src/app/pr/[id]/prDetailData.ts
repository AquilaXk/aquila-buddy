import {
  getPullRequestDraft,
  getPullRequestDraftReadiness,
  getPullRequestDraftSubmission,
  type PullRequestDraftReadiness,
  type PullRequestDraftSubmission,
  type StoredPullRequestDraft
} from "@/lib/api";

export type PrDetailOverview = {
  draft: StoredPullRequestDraft | null;
  readiness: PullRequestDraftReadiness | null;
  submission: PullRequestDraftSubmission | null;
  errors: {
    draft: boolean;
    readiness: boolean;
    submission: boolean;
  };
};

type LoadResult<T> = {
  data: T;
  error: boolean;
};

export async function loadPrDetailOverview(id: string): Promise<PrDetailOverview> {
  const [draftResult, readinessResult, submissionResult] = await Promise.all([
    loadSafely(getPullRequestDraft(id), null),
    loadSafely(getPullRequestDraftReadiness(id), null),
    loadSafely(getPullRequestDraftSubmission(id), null)
  ]);

  return {
    draft: draftResult.data,
    readiness: readinessResult.data,
    submission: submissionResult.data,
    errors: {
      draft: draftResult.error,
      readiness: readinessResult.error,
      submission: submissionResult.error
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
