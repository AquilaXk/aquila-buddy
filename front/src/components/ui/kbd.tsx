import type { HTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export function Kbd({ className, ...props }: HTMLAttributes<HTMLElement>) {
  return (
    <kbd
      className={cn(
        "inline-flex h-5 min-w-5 items-center justify-center rounded-[4px] border border-[#d1d9e0] bg-[#f6f8fa] px-1.5 font-mono text-[11px] font-medium text-[#59636e] shadow-[inset_0_-1px_0_#d1d9e0] tabular-nums",
        className
      )}
      {...props}
    />
  );
}

export function KbdGroup({ className, ...props }: HTMLAttributes<HTMLSpanElement>) {
  return <span className={cn("inline-flex items-center gap-1", className)} {...props} />;
}
