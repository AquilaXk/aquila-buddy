import type { PullRequestDraftTimeline, PullRequestDraftTimelineStatus } from "@/lib/api";

type PrDraftTimelinePanelProps = {
  hasError: boolean;
  timeline: PullRequestDraftTimeline | null;
};

const statusLabels: Record<PullRequestDraftTimelineStatus, string> = {
  DONE: "완료",
  READY: "준비됨",
  NEEDS_ATTENTION: "확인 필요"
};

const statusClassNames: Record<PullRequestDraftTimelineStatus, string> = {
  DONE: "border-github-accent bg-github-surface text-github-accent",
  READY: "border-github-border bg-github-bg text-github-muted",
  NEEDS_ATTENTION: "border-red-500/30 bg-red-500/10 text-red-400"
};

export function PrDraftTimelinePanel({ hasError, timeline }: PrDraftTimelinePanelProps) {
  if (hasError) {
    return (
      <section className="rounded-md border border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
        PR timeline을 불러오지 못했습니다.
      </section>
    );
  }

  if (!timeline) {
    return (
      <section className="rounded-md border border-github-border p-4 text-sm font-semibold text-github-muted">
        PR timeline이 준비되지 않았습니다.
      </section>
    );
  }

  return (
    <section className="rounded-md border border-github-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-github-accent">Draft Timeline</p>
          <h3 className="mt-1 text-lg font-bold text-github-text">
            {timeline.items.length}개 준비 단계
          </h3>
        </div>
        <span className="max-w-full break-all rounded-md border border-github-border px-3 py-2 text-sm font-semibold text-github-muted">
          Draft {timeline.draftId}
        </span>
      </div>

      <ol className="mt-4 divide-y divide-github-border">
        {timeline.items.map((item, index) => (
          <li className="grid gap-3 py-4 first:pt-0 last:pb-0 md:grid-cols-[2rem_1fr]" key={item.key}>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-github-bg text-sm font-bold text-github-accent">
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="text-sm font-semibold text-github-text">{item.label}</h4>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[item.status]}`}
                >
                  {statusLabels[item.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-github-muted">{item.description}</p>
              <p className="mt-2 break-words rounded-md bg-github-bg px-3 py-2 text-sm leading-6 text-github-text">
                {item.detail}
              </p>
              <p className="mt-2 text-xs font-semibold text-github-muted">{formatDate(item.occurredAt)}</p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
