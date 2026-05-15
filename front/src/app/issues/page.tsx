import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { getRecommendedIssues } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function IssuesPage() {
  const result = await loadRecommendedIssues();

  return (
    <ProtectedLayout title="Recommended Issues">
      {result.error ? (
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          추천 이슈를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      <div className="space-y-3">
        {result.issues.map((issue) => (
          <Link
            key={issue.id}
            href={`/issues/${issue.id}`}
            className="focus-ring surface block p-4 hover:border-moss"
          >
            <div className="flex flex-wrap items-center gap-2">
              <p className="text-sm font-semibold text-moss">
                {issue.repoFullName} #{issue.issueNumber}
              </p>
              <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
                {Math.round(issue.score * 100)}%
              </span>
              <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
                {issue.status}
              </span>
            </div>
            <h2 className="mt-1 text-lg font-bold">{issue.title}</h2>
            <p className="mt-2 text-sm leading-6 text-neutral-700">{issue.summary}</p>
            <p className="mt-2 text-sm leading-6 text-neutral-700">{issue.reason}</p>
            <div className="mt-3 flex flex-wrap gap-2">
              {issue.labels.map((label) => (
                <span
                  key={label}
                  className="rounded-md bg-neutral-100 px-2 py-1 text-xs font-semibold text-neutral-700"
                >
                  {label}
                </span>
              ))}
            </div>
          </Link>
        ))}
      </div>
    </ProtectedLayout>
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
