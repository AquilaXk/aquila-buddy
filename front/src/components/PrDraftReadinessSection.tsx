"use client";

import { useState } from "react";
import { CheckCircle2, AlertCircle } from "lucide-react";
import type { PullRequestDraftReadiness } from "@/lib/api";

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
    <section className="surface mb-5 overflow-hidden">
      <div className="sheet-header flex flex-wrap items-center justify-between gap-2 px-4 py-2.5">
        <div className="flex items-center gap-2">
          <h3 className="text-xs font-semibold text-ink-primary">
            PR 기여 준비도 점검
          </h3>
          <span className="tabular-nums rounded border border-neutral-border bg-white px-2 py-0.5 font-mono text-[11px] font-medium text-ink-primary">
            {readiness.readyCount} / {readiness.items.length} 준비 완료
          </span>
        </div>
        <div className="flex items-center gap-1 text-xs">
          <button
            className={`focus-ring tabular-nums rounded px-2 py-0.5 text-[11px] font-medium transition-colors ${
              filter === "all"
                ? "bg-[#121317] text-white"
                : "text-ink-secondary hover:bg-neutral-hover hover:text-ink-primary"
            }`}
            onClick={() => setFilter("all")}
            type="button"
          >
            전체 ({readiness.items.length})
          </button>
          <button
            className={`focus-ring tabular-nums rounded px-2 py-0.5 text-[11px] font-medium transition-colors ${
              filter === "action-required"
                ? "bg-[#121317] text-white"
                : "text-ink-secondary hover:bg-neutral-hover hover:text-ink-primary"
            }`}
            onClick={() => setFilter("action-required")}
            type="button"
          >
            확인 필요 ({actionRequiredCount})
          </button>
        </div>
      </div>

      <div className="h-0.5 w-full bg-neutral-border">
        <div
          className="h-full bg-[#137333] transition-all duration-300"
          style={{
            width: `${(readiness.readyCount / Math.max(readiness.items.length, 1)) * 100}%`
          }}
        />
      </div>

      <div className="divide-y divide-neutral-border">
        {filteredItems.length === 0 ? (
          <p className="px-4 py-4 text-center text-xs text-ink-secondary">
            확인 필요한 항목이 없습니다. 모든 준비도 점검 항목을 통과했습니다.
          </p>
        ) : null}
        {filteredItems.map((item) => (
          <div
            className="flex flex-col justify-between gap-1.5 px-4 py-3 sm:flex-row sm:items-start sm:gap-4"
            key={item.key}
          >
            <div className="flex min-w-0 flex-1 items-start gap-2.5">
              {item.status === "READY" ? (
                <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#137333]" />
              ) : (
                <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#b06000]" />
              )}
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-medium text-ink-primary">{item.label}</span>
                  <span
                    className={`badge-trim rounded border px-1.5 py-0.5 text-[10px] font-medium ${
                      item.status === "READY"
                        ? "border-[#137333]/20 bg-[#e6f4ea] text-[#137333]"
                        : "border-[#b06000]/20 bg-[#fef7e0] text-[#b06000]"
                    }`}
                  >
                    {item.status === "READY" ? "준비됨" : "확인 필요"}
                  </span>
                </div>
                <p className="mt-0.5 text-[11px] leading-relaxed text-ink-secondary">
                  {item.description}
                </p>
              </div>
            </div>
            <p className="shrink-0 break-all font-mono text-[11px] text-ink-muted sm:max-w-xs sm:text-right">
              {item.detail}
            </p>
          </div>
        ))}
      </div>
    </section>
  );
}
