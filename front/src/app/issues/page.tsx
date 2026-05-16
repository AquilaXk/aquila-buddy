import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  getCollectedIssues,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  type RecommendedIssue,
  type RecommendedIssueHistory
} from "@/lib/api";
import { IssueCrawlSearchPanel } from "./IssueCrawlSearchPanel";

export const dynamic = "force-dynamic";

export default async function IssuesPage() {
  const [recommendResult, catalogResult, historyResult] = await Promise.all([
    loadRecommendedIssues(),
    loadCollectedIssues(),
    loadRecommendedIssueHistory()
  ]);

  return (
    <ProtectedLayout>
      {recommendResult.error ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          추천 이슈를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      {historyResult.error ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          추천 이력을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      {catalogResult.error ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          수집 이슈 catalog를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}

      <IssueCrawlSearchPanel />

      <section className="mb-8 space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-base font-bold text-github-text">Collected catalog</h2>
          <span className="text-xs font-semibold text-github-muted">
            {catalogResult.issues.length} issues
          </span>
        </div>
        {catalogResult.issues.length > 0 ? (
          catalogResult.issues.map((issue) => (
            <IssueCard key={issue.id} issue={issue} reason={issue.reason} />
          ))
        ) : (
          <div className="surface p-4 text-sm font-semibold text-github-muted">
            수집된 이슈 catalog가 없습니다.
          </div>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="text-base font-bold text-github-text">Current recommendations</h2>
        {recommendResult.issues.map((issue) => (
          <IssueCard key={issue.id} issue={issue} reason={issue.reason} />
        ))}
      </section>

      <section className="mt-8 space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-base font-bold text-github-text">Saved history</h2>
          <span className="text-xs font-semibold text-github-muted">
            {historyResult.issues.length} issues
          </span>
        </div>
        {historyResult.issues.length > 0 ? (
          historyResult.issues.map((issue) => (
            <IssueCard
              key={issue.id}
              issue={issue}
              reason={issue.isAnalyzed ? "분석 완료" : "분석 전"}
              analysisResultId={issue.analysisResultId}
            />
          ))
        ) : (
          <div className="surface p-4 text-sm font-semibold text-github-muted">
            저장된 추천 이력이 없습니다.
          </div>
        )}
      </section>
    </ProtectedLayout>
  );
}

function IssueCard({
  issue,
  reason,
  analysisResultId
}: {
  issue: RecommendedIssue | RecommendedIssueHistory;
  reason: string;
  analysisResultId?: string | null;
}) {
  return (
    <Link
      href={`/issues/${issue.id}`}
      className="focus-ring surface block p-4 hover:border-github-accent"
    >
      <div className="flex flex-wrap items-center gap-2">
        <p className="break-all text-sm font-semibold text-github-accent">
          {issue.repoFullName} #{issue.issueNumber}
        </p>
        <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
          {Math.round(issue.score * 100)}%
        </span>
        <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
          {issue.status}
        </span>
        {analysisResultId ? (
          <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
            analysis #{analysisResultId}
          </span>
        ) : null}
      </div>
      <h3 className="mt-1 text-lg font-bold">{issue.title}</h3>
      <p className="mt-2 text-sm leading-6 text-github-muted">{issue.summary}</p>
      <p className="mt-2 text-sm leading-6 text-github-muted">{reason}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {issue.labels.map((label) => (
          <span
            key={label}
            className="rounded-md bg-github-bg px-2 py-1 text-xs font-semibold text-github-muted"
          >
            {label}
          </span>
        ))}
      </div>
    </Link>
  );
}

async function loadRecommendedIssues() {
  try {
    return {
      issues: await getRecommendedIssues(),
      error: null
    };
  } catch (error) {
    return {
      issues: [],
      error
    };
  }
}

async function loadCollectedIssues() {
  try {
    return {
      issues: await getCollectedIssues(),
      error: null
    };
  } catch (error) {
    return {
      issues: [],
      error
    };
  }
}

async function loadRecommendedIssueHistory() {
  try {
    return {
      issues: await getRecommendedIssueHistory(),
      error: null
    };
  } catch (error) {
    return {
      issues: [],
      error
    };
  }
}
