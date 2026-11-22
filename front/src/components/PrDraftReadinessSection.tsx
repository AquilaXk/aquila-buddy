"use client";

import { useState } from "react";
import { AlertCircle, CheckCircle2 } from "./icons";
import type { PullRequestDraftReadiness } from "@/lib/api";
import { cn } from "@/lib/utils";

type PrDraftReadinessSectionProps = {
  readiness: PullRequestDraftReadiness | null;
};

export function PrDraftReadinessSection({ readiness }: PrDraftReadinessSectionProps) {
  const [filter, setFilter] = useState<"all" | "action-required">("all");

  if (!readiness) {
    return null;
  }

  const filteredItems = readiness.items.filter(
    (item) => filter === "all" || item.status === "ACTION_REQUIRED"
  );
  const actionRequiredCount = readiness.items.filter(
    (item) => item.status === "ACTION_REQUIRED"
  ).length;

  return (
    <section className="primer-box mb-5 overflow-hidden">
      <div className="primer-box-header flex flex-wrap items-center justify-between gap-2 px-4 py-2.5">
        <div className="flex items-center gap-2">
          <h3 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
            PR 기여 준비도 점검
          </h3>
          <span className="tabular-nums rounded-full border border-[#d1d9e0] bg-white px-2 py-0.5 font-mono text-[11px] font-medium text-[#1f2328]">
            {readiness.readyCount} / {readiness.items.length} 준비 완료
          </span>
        </div>
        <div className="flex items-center gap-0.5 rounded-md border border-[#d1d9e0] bg-[#eaeef2] p-0.5 text-xs">
          <button
            className={cn(
              "focus-ring tabular-nums rounded-[5px] px-2.5 py-0.5 text-[11px] transition-colors",
              filter === "all"
                ? "border border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
                : "border border-transparent font-medium text-[#59636e] hover:text-[#1f2328]"
            )}
            onClick={() => setFilter("all")}
            type="button"
          >
            전체 ({readiness.items.length})
          </button>
          <button
            className={cn(
              "focus-ring tabular-nums rounded-[5px] px-2.5 py-0.5 text-[11px] transition-colors",
              filter === "action-required"
                ? "border border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
                : "border border-transparent font-medium text-[#59636e] hover:text-[#1f2328]"
            )}
            onClick={() => setFilter("action-required")}
            type="button"
          >
            확인 필요 ({actionRequiredCount})
          </button>
        </div>
      </div>

      <div className="h-1 w-full bg-[#eaeef2]">
        <div
          className="h-full bg-[#1f883d] transition-all duration-300"
          style={{
            width: `${(readiness.readyCount / Math.max(readiness.items.length, 1)) * 100}%`
          }}
        />
      </div>

      <div className="divide-y divide-[#d1d9e0]">
        {filteredItems.length === 0 ? (
          <p className="px-4 py-4 text-center text-[13px] text-[#59636e]">
            확인 필요한 항목이 없습니다. 모든 준비도 점검 항목을 통과했습니다.
          </p>
        ) : null}
        {filteredItems.map((item) => (
          <div
            className={cn(
              "flex flex-col justify-between gap-1.5 px-4 py-3 sm:flex-row sm:items-start sm:gap-4",
              item.status === "ACTION_REQUIRED" && "bg-[#fff8c5]/50"
            )}
            key={item.key}
          >
            <div className="flex min-w-0 flex-1 items-start gap-2.5">
              {item.status === "READY" ? (
                <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[#1a7f37]" />
              ) : (
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-[#9a6700]" />
              )}
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-[13px] font-semibold text-[#1f2328]">{item.label}</span>
                  <span
                    className={cn(
                      "badge-trim rounded-full border px-2 py-0.5 text-[10.5px] font-medium",
                      item.status === "READY"
                        ? "border-[#1a7f37]/30 bg-[#dafbe1] text-[#1a7f37]"
                        : "border-[#9a6700]/25 bg-[#fff8c5] text-[#9a6700]"
                    )}
                  >
                    {item.status === "READY" ? "준비됨" : "확인 필요"}
                  </span>
                </div>
                <p className="mt-0.5 text-[12.5px] leading-relaxed text-[#59636e]">
                  {item.description}
                </p>
              </div>
            </div>
            <p className="shrink-0 break-all font-mono text-[11.5px] text-[#59636e] sm:max-w-xs sm:text-right">
              {item.detail}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}
