import {
  getPullRequestDraft,
  getPullRequestDraftReadiness,
  type PullRequestDraftReadiness,
  type StoredPullRequestDraft
} from "@/lib/api";

export type PrDetailOverview = {
  draft: StoredPullRequestDraft | null;
  readiness: PullRequestDraftReadiness | null;
  errors: {
    draft: boolean;
    readiness: boolean;
  };
};

type LoadResult<T> = {
  data: T;
  error: boolean;
};

export async function loadPrDetailOverview(id: string): Promise<PrDetailOverview> {
  const [draftResult, readinessResult] = await Promise.all([
    loadSafely(getPullRequestDraft(id), null),
    loadSafely(getPullRequestDraftReadiness(id), null)
  ]);

  return {
    draft: draftResult.data,
    readiness: readinessResult.data,
    errors: {
      draft: draftResult.error,
      readiness: readinessResult.error
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
