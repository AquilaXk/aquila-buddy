import Link from "next/link";
import type { DashboardActivityItem } from "./dashboardWorkflow";

export function DashboardActivityPanel({ activities }: { activities: DashboardActivityItem[] }) {
  return (
    <section className="surface p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-github-accent">Workflow Activity</p>
          <h2 className="mt-1 text-lg font-bold text-github-text">최근 진행 기록</h2>
        </div>
        <span className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
          {activities.length} items
        </span>
      </div>

      {activities.length > 0 ? (
        <ol className="mt-4 divide-y divide-github-border">
          {activities.map((activity) => (
            <li className="py-4" key={activity.id}>
              <Link className="focus-ring block rounded-md hover:text-github-accent" href={activity.href}>
                <p className="text-sm font-bold text-github-text">{activity.label}</p>
                <p className="mt-1 break-all text-sm leading-6 text-github-muted">{activity.detail}</p>
                <p className="mt-2 text-xs font-semibold text-github-muted">
                  {formatDate(activity.createdAt)}
                </p>
              </Link>
            </li>
          ))}
        </ol>
      ) : (
        <p className="mt-4 rounded-md border border-github-border bg-github-bg px-3 py-3 text-sm font-semibold text-github-muted">
          아직 표시할 활동이 없습니다.
        </p>
      )}
    </section>
  );
}

function formatDate(value: string | null) {
  if (!value) {
    return "최근 추천";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
