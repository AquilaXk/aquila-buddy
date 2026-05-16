import {
  getPullRequestDrafts,
  getPullRequestDraftTranslationActivities,
  getRecommendedIssueHistory,
  getUserProfile,
  type PullRequestDraftTranslationActivity,
  type RecommendedIssueHistory,
  type StoredPullRequestDraft,
  type UserProfile
} from "@/lib/api";

export type DashboardOverview = {
  profile: UserProfile | null;
  issueHistory: RecommendedIssueHistory[];
  prDrafts: StoredPullRequestDraft[];
  translationActivities: PullRequestDraftTranslationActivity[];
  errors: {
    profile: boolean;
    issueHistory: boolean;
    prDrafts: boolean;
    translationActivities: boolean;
  };
};

type LoadResult<T> = {
  data: T;
  error: boolean;
};

export async function loadDashboardOverview(): Promise<DashboardOverview> {
  const [profileResult, issueHistoryResult, prDraftsResult, translationActivitiesResult] = await Promise.all([
    loadSafely(getUserProfile(), null),
    loadSafely(getRecommendedIssueHistory(), []),
    loadSafely(getPullRequestDrafts(), []),
    loadSafely(getPullRequestDraftTranslationActivities(), [])
  ]);

  return {
    profile: profileResult.data,
    issueHistory: issueHistoryResult.data,
    prDrafts: prDraftsResult.data,
    translationActivities: translationActivitiesResult.data,
    errors: {
      profile: profileResult.error,
      issueHistory: issueHistoryResult.error,
      prDrafts: prDraftsResult.error,
      translationActivities: translationActivitiesResult.error
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
