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
