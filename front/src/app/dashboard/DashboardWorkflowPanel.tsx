import Link from "next/link";
import type { DashboardWorkflow, DashboardWorkflowStatus } from "./dashboardWorkflow";

const statusLabels: Record<DashboardWorkflowStatus, string> = {
  done: "완료",
  ready: "진행 가능",
  waiting: "대기",
  blocked: "확인 필요"
};

const statusClassNames: Record<DashboardWorkflowStatus, string> = {
  done: "border-green-200 bg-green-50 text-green-800",
  ready: "border-moss bg-white text-moss",
  waiting: "border-line bg-neutral-50 text-neutral-600",
  blocked: "border-red-200 bg-red-50 text-red-700"
};

export function DashboardWorkflowPanel({ workflow }: { workflow: DashboardWorkflow }) {
  return (
    <section className="surface p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-moss">Contribution Workflow</p>
          <h2 className="mt-1 text-lg font-bold text-neutral-950">
            {workflow.completedCount} / {workflow.steps.length} 단계 완료
          </h2>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-neutral-700">
            프로필 분석부터 PR 초안까지 다음 진행 지점을 한 번에 확인합니다.
          </p>
        </div>
        <Link
          className="focus-ring rounded-md bg-moss px-4 py-2 text-sm font-semibold text-white hover:bg-moss-dark"
          href={workflow.nextAction.href}
        >
          다음 액션
        </Link>
      </div>

      <ol className="mt-5 divide-y divide-line">
        {workflow.steps.map((step, index) => (
          <li className="grid gap-3 py-4 sm:grid-cols-[2rem_1fr_auto]" key={step.id}>
            <div
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-md bg-neutral-100 text-sm font-bold text-moss"
            >
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base font-bold text-ink">{step.label}</h3>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[step.status]}`}
                >
                  {statusLabels[step.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-neutral-700">{step.description}</p>
              <p className="mt-1 break-all text-xs font-semibold text-neutral-500">{step.detail}</p>
            </div>
            <Link
              className="focus-ring self-start rounded-md border border-line px-3 py-2 text-sm font-semibold text-neutral-700 hover:border-moss hover:text-moss"
              href={step.href}
            >
              열기
            </Link>
          </li>
        ))}
      </ol>

      <div className="mt-4 rounded-md border border-line bg-neutral-50 px-3 py-2 text-sm leading-6 text-neutral-700">
        <span className="font-semibold text-ink">{workflow.nextAction.label}</span>
        <span className="mx-2 text-neutral-400">/</span>
        {workflow.nextAction.description}
      </div>
    </section>
  );
}
