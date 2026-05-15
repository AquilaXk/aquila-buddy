import { ProtectedLayout } from "@/components/ProtectedLayout";
import { getPullRequestDraft, type StoredPullRequestDraft } from "@/lib/api";
import { TranslationPanel } from "./TranslationPanel";

type PrDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function PrDetailPage({ params }: PrDetailPageProps) {
  const { id } = await params;
  const result = await loadPullRequestDraft(id);

  return (
    <ProtectedLayout title="PR Detail">
      {result.error ? (
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          PR 초안을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      <section className="surface p-5">
        <p className="text-sm font-semibold text-moss">Draft ID</p>
        <h2 className="mt-2 text-xl font-bold">{id}</h2>
        {result.draft ? (
          <DraftDetail draft={result.draft} />
        ) : (
          <p className="mt-4 text-sm leading-6 text-neutral-700">
            PR 초안 데이터가 준비되지 않았습니다.
          </p>
        )}
      </section>
    </ProtectedLayout>
  );
}

function DraftDetail({ draft }: { draft: StoredPullRequestDraft }) {
  return (
    <div className="mt-5 space-y-5">
      <div className="grid gap-3 md:grid-cols-2">
        <div>
          <p className="text-sm font-semibold">Repository</p>
          <p className="mt-1 break-all text-sm leading-6 text-neutral-700">
            {draft.upstreamRepo} #{draft.githubIssueNumber}
          </p>
        </div>
        <div>
          <p className="text-sm font-semibold">Branch</p>
          <p className="mt-1 break-all font-mono text-sm leading-6 text-neutral-700">
            {draft.baseBranch} -&gt; {draft.headBranch}
          </p>
        </div>
      </div>

      <div>
        <p className="text-sm font-semibold">Created</p>
        <p className="mt-1 text-sm leading-6 text-neutral-700">{formatDate(draft.createdAt)}</p>
      </div>

      <div>
        <h3 className="text-sm font-semibold">Title</h3>
        <p className="mt-2 rounded-md bg-neutral-100 p-3 text-sm font-semibold leading-6 text-ink">
          {draft.title}
        </p>
      </div>

      <div>
        <h3 className="text-sm font-semibold">Body</h3>
        <pre className="mt-2 overflow-auto whitespace-pre-wrap rounded-md bg-neutral-100 p-3 text-sm leading-6 text-neutral-800">
          {draft.body}
        </pre>
      </div>

      <TranslationPanel draftId={draft.id} />
    </div>
  );
}

async function loadPullRequestDraft(id: string) {
  try {
    return {
      draft: await getPullRequestDraft(id),
      error: null
    };
  } catch (error) {
    return {
      draft: null,
      error
    };
  }
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
