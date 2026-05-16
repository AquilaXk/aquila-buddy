import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { IssuePseudoCodePanel } from "./IssuePseudoCodePanel";
import { IssueGuideWorkflowPanel } from "./IssueGuideWorkflowPanel";
import { loadIssueDetailOverview, type IssueDetailIssue } from "./issueDetailData";

type IssueDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function IssueDetailPage({ params }: IssueDetailPageProps) {
  const { id } = await params;
  const result = await loadIssueDetailOverview(id);

  return (
    <ProtectedLayout title="Issue Guide">
      {result.errors.issue ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          추천 이슈 정보를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      {result.errors.guide ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          이슈 가이드를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}

      {result.issue ? (
        <section className="surface mb-5 p-5">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="break-all text-sm font-semibold text-github-accent">
                {result.issue.repoFullName} #{result.issue.issueNumber}
              </p>
              <h2 className="mt-2 text-2xl font-bold text-github-text">{result.issue.title}</h2>
              <p className="mt-3 max-w-3xl text-sm leading-6 text-github-muted">
                {result.issue.summary}
              </p>
            </div>
            {result.draftHref ? (
              <Link
                href={result.draftHref}
                className="focus-ring rounded-md bg-github-accent px-4 py-2 text-sm font-bold text-white hover:bg-github-accent-dark"
              >
                PR Draft
              </Link>
            ) : null}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
              {Math.round(result.issue.score * 100)}%
            </span>
            <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
              {result.issue.status}
            </span>
            <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
              {getIssueAnalysisLabel(result.issue)}
            </span>
            {result.issue.labels.map((label) => (
              <span
                key={label}
                className="rounded-md bg-github-bg px-2 py-1 text-xs font-semibold text-github-muted"
              >
                {label}
              </span>
            ))}
          </div>
        </section>
      ) : (
        <section className="surface mb-5 p-5">
          <p className="text-sm font-semibold text-github-accent">Issue ID</p>
          <h2 className="mt-2 text-xl font-bold">{id}</h2>
          <p className="mt-3 text-sm leading-6 text-github-muted">
            저장된 추천 이슈 정보가 없어 가이드만 표시합니다.
          </p>
        </section>
      )}

      <IssueGuideWorkflowPanel
        draftHref={result.draftHref}
        hasError={result.errors.workflow}
        workflow={result.workflow}
      />

      <IssuePseudoCodePanel
        hasError={result.errors.pseudoCode}
        pseudoCode={result.pseudoCode}
      />

      <section className="surface p-5">
        {result.guide ? (
          <div className="grid gap-5 md:grid-cols-2">
            <div>
              <h3 className="text-sm font-semibold">Guide</h3>
              <p className="mt-2 text-sm leading-6 text-github-muted">{result.guide.guideline}</p>
            </div>
            <div>
              <h3 className="text-sm font-semibold">Side Effects</h3>
              <ul className="mt-2 list-disc space-y-2 pl-4 text-sm leading-6 text-github-muted">
                {result.guide.sideEffects.map((sideEffect) => (
                  <li key={sideEffect}>{sideEffect}</li>
                ))}
              </ul>
            </div>
          </div>
        ) : (
          <p className="mt-5 text-sm leading-6 text-github-muted">
            이슈 가이드 데이터가 준비되지 않았습니다.
          </p>
        )}
      </section>
    </ProtectedLayout>
  );
}

function getIssueAnalysisLabel(issue: IssueDetailIssue): string {
  if ("analysisResultId" in issue) {
    return issue.isAnalyzed ? "분석 완료" : "분석 전";
  }

  return "현재 추천";
}
