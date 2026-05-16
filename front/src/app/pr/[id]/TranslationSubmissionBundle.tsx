"use client";

import { useEffect, useState } from "react";
import {
  getPullRequestDraftTranslationSubmission,
  type PullRequestDraftSubmissionItem,
  type PullRequestDraftTranslation,
  type PullRequestDraftTranslationSubmission
} from "@/lib/api";

type TranslationSubmissionBundleProps = {
  draftId: string;
  translation: PullRequestDraftTranslation;
};

export function TranslationSubmissionBundle({ draftId, translation }: TranslationSubmissionBundleProps) {
  const [submission, setSubmission] = useState<PullRequestDraftTranslationSubmission | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadSubmission() {
      setLoading(true);
      try {
        const loadedSubmission = await getPullRequestDraftTranslationSubmission(draftId, translation.id);
        if (mounted) {
          setSubmission(loadedSubmission);
          setError(null);
        }
      } catch (caught) {
        if (mounted) {
          setError(caught instanceof Error ? caught.message : "영문 제출 항목을 불러오지 못했습니다.");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    loadSubmission();

    return () => {
      mounted = false;
    };
  }, [draftId, translation.id]);

  async function copyItem(item: PullRequestDraftSubmissionItem) {
    await copyText(item.value);
    setCopiedKey(item.key);
    window.setTimeout(() => setCopiedKey(null), 1600);
  }

  if (loading) {
    return <p className="mt-4 text-sm font-semibold text-github-muted">영문 제출 항목을 불러오는 중...</p>;
  }

  if (error) {
    return (
      <div className="mt-4 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
        {error}
      </div>
    );
  }

  if (!submission) {
    return (
      <p className="mt-4 rounded-md border border-github-border bg-github-surface px-3 py-2 text-sm font-semibold text-github-muted">
        영문 제출 항목이 준비되지 않았습니다.
      </p>
    );
  }

  return (
    <div className="mt-4 rounded-md border border-github-border bg-github-surface p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase text-github-accent">English Submission</p>
          <h4 className="mt-1 text-sm font-semibold text-github-text">
            {submission.items.length}개 제출 항목
          </h4>
        </div>
        <span className="max-w-full break-all rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted">
          {submission.targetLanguage} / #{submission.githubIssueNumber}
        </span>
      </div>

      <div className="mt-3 space-y-2">
        {submission.items.map((item) => (
          <article className="rounded-md bg-github-bg p-3" key={item.key}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h5 className="text-sm font-semibold text-github-text">{item.label}</h5>
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
            <pre className="mt-3 max-h-44 overflow-auto whitespace-pre-wrap break-words text-sm leading-6 text-github-text">
              {item.value}
            </pre>
          </article>
        ))}
      </div>
    </div>
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
