import type { PullRequestDraftContributionReport } from "@/lib/api";

type PrDraftContributionReportPanelProps = {
  hasError: boolean;
  report: PullRequestDraftContributionReport | null;
};

export function PrDraftContributionReportPanel({
  hasError,
  report
}: PrDraftContributionReportPanelProps) {
  if (hasError) {
    return (
      <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
        Contribution report를 불러오지 못했습니다.
      </div>
    );
  }

  if (!report) {
    return (
      <div className="rounded-md border border-line bg-neutral-50 px-3 py-2 text-sm font-semibold text-neutral-600">
        Contribution report가 아직 준비되지 않았습니다.
      </div>
    );
  }

  const readinessLabel = `${report.readinessReadyCount}/${report.readinessTotalCount}`;

  return (
    <section className="rounded-md border border-line p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase text-moss">Contribution Report</p>
          <h3 className="mt-1 text-base font-bold text-ink">제출 전 요약</h3>
        </div>
        <span className="rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-600">
          {formatDate(report.createdAt)}
        </span>
      </div>

      <div className="mt-4 grid gap-3 md:grid-cols-3">
        <ReportMetric label="Readiness" value={readinessLabel} detail="준비 완료 항목" />
        <ReportMetric
          label="Translations"
          value={String(report.translationCount)}
          detail={report.latestTranslation ? "최신 영문 초안 있음" : "생성된 영문 초안 없음"}
        />
        <ReportMetric label="Issue" value={`#${report.githubIssueNumber}`} detail={report.upstreamRepo} />
      </div>

      <div className="mt-4 rounded-md bg-neutral-100 p-3">
        <p className="break-all text-sm font-semibold text-ink">{report.title}</p>
        <p className="mt-2 break-all font-mono text-xs text-neutral-600">
          {report.baseBranch} -&gt; {report.headBranch}
        </p>
      </div>

      {report.latestTranslation ? (
        <div className="mt-3 rounded-md border border-line bg-white p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="text-xs font-semibold uppercase text-moss">
              Latest {report.latestTranslation.targetLanguage}
            </p>
            <span className="text-xs font-semibold text-neutral-500">
              {formatDate(report.latestTranslation.createdAt)}
            </span>
          </div>
          <p className="mt-2 text-sm font-semibold leading-6 text-ink">
            {report.latestTranslation.title}
          </p>
        </div>
      ) : null}
    </section>
  );
}

function ReportMetric({ detail, label, value }: { detail: string; label: string; value: string }) {
  return (
    <div className="rounded-md bg-neutral-100 p-3">
      <p className="text-xs font-semibold uppercase text-neutral-500">{label}</p>
      <p className="mt-2 text-xl font-bold text-ink">{value}</p>
      <p className="mt-1 break-all text-xs font-semibold text-neutral-600">{detail}</p>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
