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
    <section className="surface mb-5 space-y-3 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h3 className="text-xs font-semibold uppercase tracking-wider text-dark-text">
            PR 기여 준비도 점검
          </h3>
          <span className="tabular-nums rounded-md border border-dark-border bg-dark-elevated px-2 py-0.5 font-mono text-xs font-medium text-dark-text">
            {readiness.readyCount} / {readiness.items.length} 준비 완료
          </span>
        </div>
        <div className="flex items-center gap-1 text-xs">
          <button
            className={`focus-ring tabular-nums rounded-md px-2.5 py-1 font-medium transition-colors ${
              filter === "all"
                ? "border border-dark-border bg-dark-elevated text-dark-text"
                : "text-dark-muted hover:text-dark-text"
            }`}
            onClick={() => setFilter("all")}
            type="button"
          >
            전체 ({readiness.items.length})
          </button>
          <button
            className={`focus-ring tabular-nums rounded-md px-2.5 py-1 font-medium transition-colors ${
              filter === "action-required"
                ? "border border-dark-border bg-dark-elevated text-dark-text"
                : "text-dark-muted hover:text-dark-text"
            }`}
            onClick={() => setFilter("action-required")}
            type="button"
          >
            확인 필요 ({actionRequiredCount})
          </button>
        </div>
      </div>

      <div className="h-1.5 w-full overflow-hidden rounded-full bg-dark-elevated">
        <div
          className="h-full rounded-full bg-brand-500 transition-all duration-300"
          style={{
            width: `${(readiness.readyCount / Math.max(readiness.items.length, 1)) * 100}%`
          }}
        />
      </div>

      <div className="divide-y divide-dark-border">
        {filteredItems.length === 0 ? (
          <p className="py-3 text-center text-xs text-dark-muted">
            확인 필요한 항목이 없습니다. 모든 준비도 점검 항목을 통과했습니다.
          </p>
        ) : null}
        {filteredItems.map((item) => (
          <div className="flex items-start gap-2.5 py-2.5 first:pt-1 last:pb-1" key={item.key}>
            {item.status === "READY" ? (
              <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[#137333]" />
            ) : (
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-[#b06000]" />
            )}
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-medium text-dark-text">{item.label}</span>
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
              <p className="mt-0.5 text-[11px] leading-relaxed text-dark-muted">{item.description}</p>
              <p className="mt-1 break-words rounded border border-dark-border bg-dark-elevated px-2 py-1 font-mono text-[11px] text-dark-text">
                {item.detail}
              </p>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
