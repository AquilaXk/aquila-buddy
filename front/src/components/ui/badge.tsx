import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export type BadgeVariant =
  | "default"
  | "secondary"
  | "outline"
  | "accent"
  | "success"
  | "warning"
  | "danger"
  | "done"
  | "counter";

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  variant?: BadgeVariant;
}

const BADGE_BASE_CLASS =
  "badge-trim inline-flex items-center gap-1 rounded-full border whitespace-nowrap transition-colors";

const BADGE_STANDARD_LAYOUT = "px-2 py-0.5 text-[11.5px] font-medium";

const BADGE_VARIANT_CLASSES: Record<BadgeVariant, string> = {
  default: `${BADGE_STANDARD_LAYOUT} border-transparent bg-[#1f2328] text-white`,
  secondary: `${BADGE_STANDARD_LAYOUT} border-[#d1d9e0] bg-[#f6f8fa] text-[#1f2328]`,
  outline: `${BADGE_STANDARD_LAYOUT} border-[#d1d9e0] bg-white text-[#59636e]`,
  accent: `${BADGE_STANDARD_LAYOUT} border-[#0969da]/20 bg-[#ddf4ff] text-[#0969da]`,
  success: `${BADGE_STANDARD_LAYOUT} border-[#1a7f37]/20 bg-[#dafbe1] text-[#1a7f37]`,
  warning: `${BADGE_STANDARD_LAYOUT} border-[#9a6700]/25 bg-[#fff8c5] text-[#9a6700]`,
  danger: `${BADGE_STANDARD_LAYOUT} border-[#d1242f]/20 bg-[#ffebe9] text-[#d1242f]`,
  done: `${BADGE_STANDARD_LAYOUT} border-[#8250df]/20 bg-[#f1e5ff] text-[#8250df]`,
  counter:
    "border-transparent bg-[rgba(175,184,193,0.2)] px-1.5 py-0.5 text-[11px] font-semibold tabular-nums text-[#1f2328]",
};

export function Badge({ className, variant = "default", ...props }: BadgeProps) {
  return <span className={cn(BADGE_BASE_CLASS, BADGE_VARIANT_CLASSES[variant], className)} {...props} />;
}
