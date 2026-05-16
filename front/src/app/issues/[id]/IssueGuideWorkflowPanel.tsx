import Link from "next/link";
import type { IssueGuideWorkflow, IssueGuideWorkflowStatus } from "@/lib/api";

const statusLabels: Record<IssueGuideWorkflowStatus, string> = {
  READY: "준비됨",
  WAITING: "대기"
};

const statusClassNames: Record<IssueGuideWorkflowStatus, string> = {
  READY: "border-github-accent bg-github-surface text-github-accent",
  WAITING: "border-github-border bg-github-bg text-github-muted"
};

type IssueGuideWorkflowPanelProps = {
  workflow: IssueGuideWorkflow | null;
  draftHref: string | null;
  hasError: boolean;
};

export function IssueGuideWorkflowPanel({
  workflow,
  draftHref,
  hasError
}: IssueGuideWorkflowPanelProps) {
  if (hasError) {
    return (
      <section className="surface mb-5 border-red-500/30 bg-red-500/10 p-5 text-sm font-semibold text-red-400">
        가이드 workflow를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
      </section>
    );
  }

  if (!workflow) {
    return (
      <section className="surface mb-5 p-5 text-sm font-semibold text-github-muted">
        가이드 workflow 데이터가 준비되지 않았습니다.
      </section>
    );
  }

  return (
    <section className="surface mb-5 p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-github-accent">Guide Workflow</p>
          <h3 className="mt-1 text-lg font-bold text-github-text">
            {workflow.readyCount} / {workflow.steps.length} 단계 준비됨
          </h3>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-github-muted">
            가이드 확인부터 PR 초안 작성까지 필요한 작업 순서를 점검합니다.
          </p>
        </div>
        {draftHref ? (
          <Link
            className="focus-ring rounded-md bg-github-accent px-4 py-2 text-sm font-bold text-white hover:bg-github-accent-dark"
            href={draftHref}
          >
            PR Draft
          </Link>
        ) : null}
      </div>

      <ol className="mt-5 divide-y divide-github-border">
        {workflow.steps.map((step, index) => (
          <li className="grid gap-3 py-4 sm:grid-cols-[2rem_1fr]" key={step.key}>
            <div
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-md bg-github-bg text-sm font-bold text-github-accent"
            >
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="text-base font-bold text-github-text">{step.label}</h4>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[step.status]}`}
                >
                  {statusLabels[step.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-github-muted">{step.description}</p>
              <p className="mt-1 whitespace-pre-wrap break-words text-xs font-semibold leading-5 text-github-muted">
                {step.detail}
              </p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
