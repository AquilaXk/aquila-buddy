import type { PullRequestDraftReadiness, PullRequestDraftReadinessStatus } from "@/lib/api";

type PrDraftReadinessPanelProps = {
  hasError: boolean;
  readiness: PullRequestDraftReadiness | null;
};

const statusLabels: Record<PullRequestDraftReadinessStatus, string> = {
  READY: "준비됨",
  ACTION_REQUIRED: "확인 필요"
};

const statusClassNames: Record<PullRequestDraftReadinessStatus, string> = {
  READY: "border-moss bg-white text-moss",
  ACTION_REQUIRED: "border-red-200 bg-red-50 text-red-700"
};

export function PrDraftReadinessPanel({ hasError, readiness }: PrDraftReadinessPanelProps) {
  if (hasError) {
    return (
      <section className="rounded-md border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
        PR readiness checklist를 불러오지 못했습니다.
      </section>
    );
  }

  if (!readiness) {
    return (
      <section className="rounded-md border border-line p-4 text-sm font-semibold text-neutral-600">
        PR readiness checklist가 준비되지 않았습니다.
      </section>
    );
  }

  const totalCount = readiness.items.length;

  return (
    <section className="rounded-md border border-line p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-moss">Readiness Checklist</p>
          <h3 className="mt-1 text-lg font-bold text-ink">
            {readiness.readyCount} / {totalCount} 항목 준비됨
          </h3>
        </div>
        <span className="max-w-full break-all rounded-md border border-line px-3 py-2 text-sm font-semibold text-neutral-700">
          Draft {readiness.draftId}
        </span>
      </div>

      <ol className="mt-4 divide-y divide-line">
        {readiness.items.map((item, index) => (
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
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
