import type { RecommendedIssue } from "@/lib/api";
import { cn } from "@/lib/utils";

export type StatusBadgeTone = "open" | "closed" | "analyzed" | "info" | "warning" | "neutral";

const TONE_CLASS_MAP: Record<StatusBadgeTone, string> = {
  open: "border-[#1a7f37]/30 bg-[#dafbe1] text-[#1a7f37]",
  closed: "border-[#8250df]/25 bg-[#f1e5ff] text-[#8250df]",
  analyzed: "border-[#0969da]/25 bg-[#ddf4ff] text-[#0969da]",
  info: "border-[#0969da]/25 bg-[#ddf4ff] text-[#0969da]",
  warning: "border-[#9a6700]/25 bg-[#fff8c5] text-[#9a6700]",
  neutral: "border-[#d1d9e0] bg-[#f6f8fa] text-[#59636e]",
};

type StatusBadgeProps = {
  status: RecommendedIssue["status"];
  tone?: StatusBadgeTone;
};

export function StatusBadge({ status, tone }: StatusBadgeProps) {
  const resolvedTone: StatusBadgeTone = tone ?? (status === "OPEN" ? "open" : "closed");

  return (
    <span
      className={cn(
        "badge-trim inline-flex items-center rounded-full border px-2 py-0.5 font-mono text-[11px] font-semibold tabular-nums",
        TONE_CLASS_MAP[resolvedTone]
      )}
    >
      {status === "OPEN" ? "● OPEN" : "○ CLOSED"}
    </span>
  );
}
