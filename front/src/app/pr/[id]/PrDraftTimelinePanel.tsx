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
  DONE: "border-moss bg-white text-moss",
  READY: "border-line bg-neutral-50 text-neutral-700",
  NEEDS_ATTENTION: "border-red-200 bg-red-50 text-red-700"
};

export function PrDraftTimelinePanel({ hasError, timeline }: PrDraftTimelinePanelProps) {
  if (hasError) {
    return (
      <section className="rounded-md border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
        PR timeline을 불러오지 못했습니다.
      </section>
    );
  }

  if (!timeline) {
    return (
      <section className="rounded-md border border-line p-4 text-sm font-semibold text-neutral-600">
        PR timeline이 준비되지 않았습니다.
      </section>
    );
  }

  return (
    <section className="rounded-md border border-line p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-moss">Draft Timeline</p>
          <h3 className="mt-1 text-lg font-bold text-ink">
            {timeline.items.length}개 준비 단계
          </h3>
        </div>
        <span className="max-w-full break-all rounded-md border border-line px-3 py-2 text-sm font-semibold text-neutral-700">
          Draft {timeline.draftId}
        </span>
      </div>

      <ol className="mt-4 divide-y divide-line">
        {timeline.items.map((item, index) => (
          <li className="grid gap-3 py-4 first:pt-0 last:pb-0 md:grid-cols-[2rem_1fr]" key={item.key}>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-neutral-100 text-sm font-bold text-moss">
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="text-sm font-semibold text-ink">{item.label}</h4>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[item.status]}`}
                >
                  {statusLabels[item.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-neutral-700">{item.description}</p>
              <p className="mt-2 break-words rounded-md bg-neutral-100 px-3 py-2 text-sm leading-6 text-neutral-800">
                {item.detail}
              </p>
              <p className="mt-2 text-xs font-semibold text-neutral-500">{formatDate(item.occurredAt)}</p>
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
