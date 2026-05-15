import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { loadDashboardOverview, type DashboardOverview } from "./dashboardData";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const overview = await loadDashboardOverview();
  const lanes = buildSummaryLanes(overview);

  return (
    <ProtectedLayout title="Dashboard">
      <div className="grid gap-4 md:grid-cols-3">
        {lanes.map((lane) => (
          <section key={lane.label} className="surface p-4">
            <p className="text-sm font-semibold text-neutral-500">{lane.label}</p>
            <p className="mt-3 text-2xl font-bold text-ink">{lane.value}</p>
            <p className="mt-2 text-sm leading-6 text-neutral-700">{lane.detail}</p>
            {lane.error ? (
              <p className="mt-2 text-xs font-semibold text-red-700">backend API 확인 필요</p>
            ) : null}
          </section>
        ))}
      </div>

      <section className="mt-8 space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-base font-bold text-neutral-900">Recent Issues</h2>
          <Link className="focus-ring text-sm font-semibold text-moss" href="/issues">
            전체 보기
          </Link>
        </div>
        {overview.errors.issueHistory ? (
          <div className="surface border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
            추천 이력을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
          </div>
        ) : null}
        {overview.issueHistory.length > 0 ? (
          <div className="grid gap-3">
            {overview.issueHistory.slice(0, 3).map((issue) => (
              <Link
                className="focus-ring surface block p-4 hover:border-moss"
                href={`/issues/${issue.id}`}
                key={issue.id}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <p className="break-all text-sm font-semibold text-moss">
                    {issue.repoFullName} #{issue.issueNumber}
                  </p>
                  <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
                    {issue.isAnalyzed ? "분석 완료" : "분석 전"}
                  </span>
                </div>
                <h3 className="mt-2 text-base font-bold leading-6 text-ink">{issue.title}</h3>
                <p className="mt-2 text-sm leading-6 text-neutral-700">{issue.summary}</p>
              </Link>
            ))}
          </div>
        ) : (
          <div className="surface p-4 text-sm font-semibold text-neutral-600">
            저장된 추천 이력이 없습니다.
          </div>
        )}
      </section>
    </ProtectedLayout>
  );
}

function buildSummaryLanes(overview: DashboardOverview) {
  const analyzedIssueCount = overview.issueHistory.filter((issue) => issue.isAnalyzed).length;
  const latestDraft = overview.prDrafts.at(0);

  return [
    {
      label: "Profile",
      value: overview.profile?.profileReady ? "Ready" : "Pending",
      detail: overview.profile
        ? `@${overview.profile.login} · ${overview.profile.primaryLanguages.length} languages`
        : "GitHub OAuth 연동 후 분석 가능",
      error: overview.errors.profile
    },
    {
      label: "Issues",
      value: String(overview.issueHistory.length),
      detail: `${analyzedIssueCount}개 분석 완료 · ${overview.issueHistory.length - analyzedIssueCount}개 분석 전`,
      error: overview.errors.issueHistory
    },
    {
      label: "PR Drafts",
      value: String(overview.prDrafts.length),
      detail: latestDraft
        ? `${latestDraft.upstreamRepo} #${latestDraft.githubIssueNumber} 최근 초안`
        : "저장된 PR 초안 없음",
      error: overview.errors.prDrafts
    }
  ];
}
