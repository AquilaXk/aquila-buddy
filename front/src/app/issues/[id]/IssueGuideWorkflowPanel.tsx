import Link from "next/link";
import type { IssueGuideWorkflow, IssueGuideWorkflowStatus } from "@/lib/api";

const statusLabels: Record<IssueGuideWorkflowStatus, string> = {
  READY: "준비됨",
  WAITING: "대기"
};

const statusClassNames: Record<IssueGuideWorkflowStatus, string> = {
  READY: "border-moss bg-white text-moss",
  WAITING: "border-line bg-neutral-50 text-neutral-600"
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
      <section className="surface mb-5 border-red-200 bg-red-50 p-5 text-sm font-semibold text-red-700">
        가이드 workflow를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
      </section>
    );
  }

  if (!workflow) {
    return (
      <section className="surface mb-5 p-5 text-sm font-semibold text-neutral-600">
        가이드 workflow 데이터가 준비되지 않았습니다.
      </section>
    );
  }

  return (
    <section className="surface mb-5 p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-moss">Guide Workflow</p>
          <h3 className="mt-1 text-lg font-bold text-neutral-950">
            {workflow.readyCount} / {workflow.steps.length} 단계 준비됨
          </h3>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-neutral-700">
            가이드 확인부터 PR 초안 작성까지 필요한 작업 순서를 점검합니다.
          </p>
        </div>
        {draftHref ? (
          <Link
            className="focus-ring rounded-md bg-moss px-4 py-2 text-sm font-bold text-white hover:bg-moss-dark"
            href={draftHref}
          >
            PR Draft
          </Link>
        ) : null}
      </div>

      <ol className="mt-5 divide-y divide-line">
        {workflow.steps.map((step, index) => (
          <li className="grid gap-3 py-4 sm:grid-cols-[2rem_1fr]" key={step.key}>
            <div
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-md bg-neutral-100 text-sm font-bold text-moss"
            >
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h4 className="text-base font-bold text-ink">{step.label}</h4>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[step.status]}`}
                >
                  {statusLabels[step.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-neutral-700">{step.description}</p>
              <p className="mt-1 whitespace-pre-wrap break-words text-xs font-semibold leading-5 text-neutral-500">
                {step.detail}
              </p>
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}
