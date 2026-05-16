import { ProtectedLayout } from "@/components/ProtectedLayout";
import type {
  PullRequestDraftContributionReport,
  PullRequestDraftReadiness,
  PullRequestDraftSubmission,
  PullRequestDraftTimeline,
  StoredPullRequestDraft
} from "@/lib/api";
import { PrDraftContributionReportPanel } from "./PrDraftContributionReportPanel";
import { PrDraftEditPanel } from "./PrDraftEditPanel";
import { PrDraftReadinessPanel } from "./PrDraftReadinessPanel";
import { PrDraftSubmissionPanel } from "./PrDraftSubmissionPanel";
import { PrDraftTimelinePanel } from "./PrDraftTimelinePanel";
import { loadPrDetailOverview } from "./prDetailData";
import { TranslationPanel } from "./TranslationPanel";

type PrDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export const dynamic = "force-dynamic";

export default async function PrDetailPage({ params }: PrDetailPageProps) {
  const { id } = await params;
  const result = await loadPrDetailOverview(id);

  return (
    <ProtectedLayout title="PR Detail">
      {result.errors.draft ? (
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          PR 초안을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      <section className="surface p-5">
        <p className="text-sm font-semibold text-moss">Draft ID</p>
        <h2 className="mt-2 text-xl font-bold">{id}</h2>
        {result.draft ? (
          <DraftDetail
            draft={result.draft}
            hasReportError={result.errors.report}
            hasReadinessError={result.errors.readiness}
            hasSubmissionError={result.errors.submission}
            hasTimelineError={result.errors.timeline}
            report={result.report}
            readiness={result.readiness}
            submission={result.submission}
            timeline={result.timeline}
          />
        ) : (
          <p className="mt-4 text-sm leading-6 text-neutral-700">
            PR 초안 데이터가 준비되지 않았습니다.
          </p>
        )}
      </section>
    </ProtectedLayout>
  );
}

function DraftDetail({
  draft,
  hasReportError,
  hasReadinessError,
  hasSubmissionError,
  hasTimelineError,
  report,
  readiness,
  submission,
  timeline
}: {
  draft: StoredPullRequestDraft;
  hasReportError: boolean;
  hasReadinessError: boolean;
  hasSubmissionError: boolean;
  hasTimelineError: boolean;
  report: PullRequestDraftContributionReport | null;
  readiness: PullRequestDraftReadiness | null;
  submission: PullRequestDraftSubmission | null;
  timeline: PullRequestDraftTimeline | null;
}) {
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

      <PrDraftContributionReportPanel hasError={hasReportError} report={report} />
      <PrDraftReadinessPanel hasError={hasReadinessError} readiness={readiness} />
      <PrDraftTimelinePanel hasError={hasTimelineError} timeline={timeline} />
      <PrDraftSubmissionPanel hasError={hasSubmissionError} submission={submission} />
      <PrDraftEditPanel draft={draft} />

      <TranslationPanel draftId={draft.id} />
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
