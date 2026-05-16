import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { DashboardActivityPanel } from "./DashboardActivityPanel";
import { DashboardWorkflowPanel } from "./DashboardWorkflowPanel";
import { loadDashboardOverview, type DashboardOverview } from "./dashboardData";
import { buildDashboardActivities, buildDashboardWorkflow } from "./dashboardWorkflow";

export const dynamic = "force-dynamic";

export default async function DashboardPage() {
  const overview = await loadDashboardOverview();
  const lanes = buildSummaryLanes(overview);
  const workflow = buildDashboardWorkflow(overview);
  const activities = buildDashboardActivities(overview);

  return (
    <ProtectedLayout title="Dashboard">
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {lanes.map((lane) => (
          <section key={lane.label} className="surface p-4">
            <p className="text-sm font-semibold text-github-muted">{lane.label}</p>
            <p className="mt-3 text-2xl font-bold text-github-text">{lane.value}</p>
            <p className="mt-2 text-sm leading-6 text-github-muted">{lane.detail}</p>
            {lane.error ? (
              <p className="mt-2 text-xs font-semibold text-red-400">backend API 확인 필요</p>
            ) : null}
          </section>
        ))}
      </div>

      <div className="mt-6 grid gap-5 lg:grid-cols-[1.35fr_0.65fr]">
        <DashboardWorkflowPanel workflow={workflow} />
        <DashboardActivityPanel activities={activities} />
      </div>

      <section className="mt-8 space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-base font-bold text-github-text">Recent Issues</h2>
          <Link className="focus-ring text-sm font-semibold text-github-accent" href="/issues">
            전체 보기
          </Link>
        </div>
        {overview.errors.issueHistory ? (
          <div className="surface border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
            추천 이력을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
          </div>
        ) : null}
        {overview.issueHistory.length > 0 ? (
          <div className="grid gap-3">
            {overview.issueHistory.slice(0, 3).map((issue) => (
              <Link
                className="focus-ring surface block p-4 hover:border-github-accent"
                href={`/issues/${issue.id}`}
                key={issue.id}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <p className="break-all text-sm font-semibold text-github-accent">
                    {issue.repoFullName} #{issue.issueNumber}
                  </p>
                  <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
                    {issue.isAnalyzed ? "분석 완료" : "분석 전"}
                  </span>
                </div>
                <h3 className="mt-2 text-base font-bold leading-6 text-github-text">{issue.title}</h3>
                <p className="mt-2 text-sm leading-6 text-github-muted">{issue.summary}</p>
              </Link>
            ))}
          </div>
        ) : !overview.errors.issueHistory ? (
          <div className="surface p-4 text-sm font-semibold text-github-muted">
            저장된 추천 이력이 없습니다.
          </div>
        ) : null}
      </section>

      <section className="mt-8 space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 className="text-base font-bold text-github-text">Recent PR Drafts</h2>
          <Link className="focus-ring text-sm font-semibold text-github-accent" href="/pr-draft">
            초안 생성
          </Link>
        </div>
        {overview.errors.prDrafts ? (
          <div className="surface border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
            PR 초안 이력을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
          </div>
        ) : null}
        {overview.prDrafts.length > 0 ? (
          <div className="grid gap-3 lg:grid-cols-3">
            {overview.prDrafts.slice(0, 3).map((draft) => (
              <Link
                className="focus-ring surface block p-4 hover:border-github-accent"
                href={`/pr/${draft.id}`}
                key={draft.id}
              >
                <p className="break-all text-sm font-semibold text-github-accent">
                  {draft.upstreamRepo} #{draft.githubIssueNumber}
                </p>
                <h3 className="mt-2 line-clamp-2 text-base font-bold leading-6 text-github-text">
                  {draft.title}
                </h3>
                <p className="mt-3 break-all font-mono text-xs text-github-muted">
                  {draft.baseBranch} -&gt; {draft.headBranch}
                </p>
                <p className="mt-3 text-xs font-semibold text-github-muted">
                  {formatDate(draft.createdAt)}
                </p>
              </Link>
            ))}
          </div>
        ) : !overview.errors.prDrafts ? (
          <div className="surface p-4 text-sm font-semibold text-github-muted">
            저장된 PR 초안이 없습니다.
          </div>
        ) : null}
      </section>
    </ProtectedLayout>
  );
}

function buildSummaryLanes(overview: DashboardOverview) {
  const analyzedIssueCount = overview.issueHistory.filter((issue) => issue.isAnalyzed).length;
  const latestDraft = overview.prDrafts.at(0);
  const latestTranslation = overview.translationActivities.at(0);

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
    },
    {
      label: "Translations",
      value: String(overview.translationActivities.length),
      detail: latestTranslation
        ? `${latestTranslation.upstreamRepo} #${latestTranslation.githubIssueNumber} · ${latestTranslation.targetLanguage.toUpperCase()}`
        : "저장된 영어 번역 없음",
      error: overview.errors.translationActivities
    }
  ];
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
