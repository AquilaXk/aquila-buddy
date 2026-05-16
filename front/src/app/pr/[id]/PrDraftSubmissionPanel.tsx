"use client";

import { useState } from "react";
import type { PullRequestDraftSubmission, PullRequestDraftSubmissionItem } from "@/lib/api";

type PrDraftSubmissionPanelProps = {
  hasError: boolean;
  submission: PullRequestDraftSubmission | null;
};

export function PrDraftSubmissionPanel({ hasError, submission }: PrDraftSubmissionPanelProps) {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  async function copyItem(item: PullRequestDraftSubmissionItem) {
    await copyText(item.value);
    setCopiedKey(item.key);
    window.setTimeout(() => setCopiedKey(null), 1600);
  }

  if (hasError) {
    return (
      <section className="rounded-md border border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
        PR submission bundle을 불러오지 못했습니다.
      </section>
    );
  }

  if (!submission) {
    return (
      <section className="rounded-md border border-github-border p-4 text-sm font-semibold text-github-muted">
        PR submission bundle이 준비되지 않았습니다.
      </section>
    );
  }

  return (
    <section className="rounded-md border border-github-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-github-accent">Submission Bundle</p>
          <h3 className="mt-1 text-lg font-bold text-github-text">
            {submission.items.length}개 제출 항목
          </h3>
        </div>
        <span className="max-w-full break-all rounded-md border border-github-border px-3 py-2 text-sm font-semibold text-github-muted">
          {submission.upstreamRepo} #{submission.githubIssueNumber}
        </span>
      </div>

      <div className="mt-4 space-y-3">
        {submission.items.map((item) => (
          <article className="rounded-md bg-github-bg p-3" key={item.key}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h4 className="text-sm font-semibold text-github-text">{item.label}</h4>
                <p className="mt-1 text-xs font-semibold text-github-muted">{item.description}</p>
              </div>
              <button
                className="focus-ring min-w-16 rounded-md border border-github-border bg-github-surface px-3 py-2 text-sm font-semibold text-github-muted hover:border-github-accent hover:text-github-accent"
                onClick={() => copyItem(item)}
                type="button"
              >
                {copiedKey === item.key ? "복사됨" : "복사"}
              </button>
            </div>
            <pre className="mt-3 max-h-56 overflow-auto whitespace-pre-wrap break-words text-sm leading-6 text-github-text">
              {item.value}
            </pre>
          </article>
        ))}
      </div>
    </section>
  );
}

async function copyText(value: string) {
  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value);
      return;
    } catch {
      // 권한 제한 환경에서는 아래 DOM 기반 fallback으로 한 번 더 시도합니다.
    }
  }

  const textArea = document.createElement("textarea");
  textArea.value = value;
  textArea.setAttribute("readonly", "true");
  textArea.style.position = "fixed";
  textArea.style.left = "-9999px";
  document.body.appendChild(textArea);
  textArea.select();
  document.execCommand("copy");
  document.body.removeChild(textArea);
}
