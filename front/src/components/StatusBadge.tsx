import type { RecommendedIssue } from "@/lib/api";

type StatusBadgeProps = {
  status: RecommendedIssue["status"];
};

export function StatusBadge({ status }: StatusBadgeProps) {
  return (
    <span
      className={`badge-trim inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium tabular-nums ${
        status === "OPEN"
          ? "border-[#137333]/20 bg-[#e6f4ea] text-[#137333]"
          : "border-dark-border bg-dark-elevated text-dark-muted"
      }`}
    >
      {status === "OPEN" ? "● OPEN" : "○ CLOSED"}
    </span>
  );
}
