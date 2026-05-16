import Link from "next/link";
import type { DashboardWorkflow, DashboardWorkflowStatus } from "./dashboardWorkflow";

const statusLabels: Record<DashboardWorkflowStatus, string> = {
  done: "완료",
  ready: "진행 가능",
  waiting: "대기",
  blocked: "확인 필요"
};

const statusClassNames: Record<DashboardWorkflowStatus, string> = {
  done: "border-green-500/30 bg-green-500/10 text-green-400",
  ready: "border-github-accent bg-github-surface text-github-accent",
  waiting: "border-github-border bg-github-bg text-github-muted",
  blocked: "border-red-500/30 bg-red-500/10 text-red-400"
};

export function DashboardWorkflowPanel({ workflow }: { workflow: DashboardWorkflow }) {
  return (
    <section className="surface p-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-github-accent">Contribution Workflow</p>
          <h2 className="mt-1 text-lg font-bold text-github-text">
            {workflow.completedCount} / {workflow.steps.length} 단계 완료
          </h2>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-github-muted">
            프로필 분석부터 PR 초안까지 다음 진행 지점을 한 번에 확인합니다.
          </p>
        </div>
        <Link
          className="focus-ring rounded-md bg-github-accent px-4 py-2 text-sm font-semibold text-white hover:bg-github-accent-dark"
          href={workflow.nextAction.href}
        >
          다음 액션
        </Link>
      </div>

      <ol className="mt-5 divide-y divide-github-border">
        {workflow.steps.map((step, index) => (
          <li className="grid gap-3 py-4 sm:grid-cols-[2rem_1fr_auto]" key={step.id}>
            <div
              aria-hidden="true"
              className="flex h-8 w-8 items-center justify-center rounded-md bg-github-bg text-sm font-bold text-github-accent"
            >
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-base font-bold text-github-text">{step.label}</h3>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[step.status]}`}
                >
                  {statusLabels[step.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-github-muted">{step.description}</p>
              <p className="mt-1 break-all text-xs font-semibold text-github-muted">{step.detail}</p>
            </div>
            <Link
              className="focus-ring self-start rounded-md border border-github-border px-3 py-2 text-sm font-semibold text-github-muted hover:border-github-accent hover:text-github-accent"
              href={step.href}
            >
              열기
            </Link>
          </li>
        ))}
      </ol>

      <div className="mt-4 rounded-md border border-github-border bg-github-bg px-3 py-2 text-sm leading-6 text-github-muted">
        <span className="font-semibold text-github-text">{workflow.nextAction.label}</span>
        <span className="mx-2 text-neutral-400">/</span>
        {workflow.nextAction.description}
      </div>
    </section>
  );
}
