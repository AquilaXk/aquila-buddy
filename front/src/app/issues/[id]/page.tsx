import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
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
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          추천 이슈 정보를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      {result.errors.guide ? (
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          이슈 가이드를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}

      {result.issue ? (
        <section className="surface mb-5 p-5">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <p className="break-all text-sm font-semibold text-moss">
                {result.issue.repoFullName} #{result.issue.issueNumber}
              </p>
              <h2 className="mt-2 text-2xl font-bold text-neutral-950">{result.issue.title}</h2>
              <p className="mt-3 max-w-3xl text-sm leading-6 text-neutral-700">
                {result.issue.summary}
              </p>
            </div>
            {result.draftHref ? (
              <Link
                href={result.draftHref}
                className="focus-ring rounded-md bg-moss px-4 py-2 text-sm font-bold text-white hover:bg-moss-dark"
              >
                PR Draft
              </Link>
            ) : null}
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
              {Math.round(result.issue.score * 100)}%
            </span>
            <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
              {result.issue.status}
            </span>
            <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
              {getIssueAnalysisLabel(result.issue)}
            </span>
            {result.issue.labels.map((label) => (
              <span
                key={label}
                className="rounded-md bg-neutral-100 px-2 py-1 text-xs font-semibold text-neutral-700"
              >
                {label}
              </span>
            ))}
          </div>
        </section>
      ) : (
        <section className="surface mb-5 p-5">
          <p className="text-sm font-semibold text-moss">Issue ID</p>
          <h2 className="mt-2 text-xl font-bold">{id}</h2>
          <p className="mt-3 text-sm leading-6 text-neutral-700">
            저장된 추천 이슈 정보가 없어 가이드만 표시합니다.
          </p>
        </section>
      )}

      <section className="surface p-5">
        {result.guide ? (
          <div className="grid gap-5 md:grid-cols-3">
            <div>
              <h3 className="text-sm font-semibold">Guide</h3>
              <p className="mt-2 text-sm leading-6 text-neutral-700">{result.guide.guideline}</p>
            </div>
            <div>
              <h3 className="text-sm font-semibold">Pseudo Code</h3>
              <pre className="mt-2 whitespace-pre-wrap rounded-md bg-neutral-100 p-3 text-sm leading-6 text-neutral-800">
                {result.guide.pseudoCode}
              </pre>
            </div>
            <div>
              <h3 className="text-sm font-semibold">Side Effects</h3>
              <ul className="mt-2 list-disc space-y-2 pl-4 text-sm leading-6 text-neutral-700">
                {result.guide.sideEffects.map((sideEffect) => (
                  <li key={sideEffect}>{sideEffect}</li>
                ))}
              </ul>
            </div>
          </div>
        ) : (
          <p className="mt-5 text-sm leading-6 text-neutral-700">
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
