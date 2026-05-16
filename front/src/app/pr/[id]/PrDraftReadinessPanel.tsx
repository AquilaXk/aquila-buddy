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
  READY: "border-github-accent bg-github-surface text-github-accent",
  ACTION_REQUIRED: "border-red-500/30 bg-red-500/10 text-red-400"
};

export function PrDraftReadinessPanel({ hasError, readiness }: PrDraftReadinessPanelProps) {
  if (hasError) {
    return (
      <section className="rounded-md border border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
        PR readiness checklist를 불러오지 못했습니다.
      </section>
    );
  }

  if (!readiness) {
    return (
      <section className="rounded-md border border-github-border p-4 text-sm font-semibold text-github-muted">
        PR readiness checklist가 준비되지 않았습니다.
      </section>
    );
  }

  const totalCount = readiness.items.length;

  return (
    <section className="rounded-md border border-github-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-github-accent">Readiness Checklist</p>
          <h3 className="mt-1 text-lg font-bold text-github-text">
            {readiness.readyCount} / {totalCount} 항목 준비됨
          </h3>
        </div>
        <span className="max-w-full break-all rounded-md border border-github-border px-3 py-2 text-sm font-semibold text-github-muted">
          Draft {readiness.draftId}
        </span>
      </div>

      <ol className="mt-4 divide-y divide-github-border">
        {readiness.items.map((item, index) => (
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
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
