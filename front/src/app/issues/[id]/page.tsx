import { ProtectedLayout } from "@/components/ProtectedLayout";
import { getIssueGuide } from "@/lib/api";

type IssueDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function IssueDetailPage({ params }: IssueDetailPageProps) {
  const { id } = await params;
  const result = await loadIssueGuide(id);

  return (
    <ProtectedLayout title="Issue Guide">
      {result.error ? (
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          이슈 가이드를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      <section className="surface p-5">
        <p className="text-sm font-semibold text-moss">Issue ID</p>
        <h2 className="mt-2 text-xl font-bold">{id}</h2>
        {result.guide ? (
          <div className="mt-5 grid gap-5 md:grid-cols-3">
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

async function loadIssueGuide(issueId: string) {
  try {
    return {
      guide: await getIssueGuide(issueId),
      error: null
    };
  } catch (error) {
    return {
      guide: null,
      error
    };
  }
}
