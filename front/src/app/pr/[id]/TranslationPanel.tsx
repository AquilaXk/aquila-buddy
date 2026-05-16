"use client";

import { useEffect, useState } from "react";
import {
  getPullRequestDraftTranslations,
  translatePullRequestDraft,
  type PullRequestDraftTranslateResult,
  type PullRequestDraftTranslation
} from "@/lib/api";
import { TranslationReadinessPanel } from "./TranslationReadinessPanel";
import { TranslationSubmissionBundle } from "./TranslationSubmissionBundle";
import { TranslationTimelinePanel } from "./TranslationTimelinePanel";

type TranslationPanelProps = {
  draftId: string;
};

export function TranslationPanel({ draftId }: TranslationPanelProps) {
  const [translations, setTranslations] = useState<PullRequestDraftTranslation[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [translateResult, setTranslateResult] = useState<PullRequestDraftTranslateResult | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadTranslations() {
      try {
        const loadedTranslations = await getPullRequestDraftTranslations(draftId);
        if (mounted) {
          setTranslations(loadedTranslations);
          setError(null);
        }
      } catch (caught) {
        if (mounted) {
          setError(caught instanceof Error ? caught.message : "PR 초안 번역을 불러오지 못했습니다.");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    loadTranslations();

    return () => {
      mounted = false;
    };
  }, [draftId]);

  async function handleCreateTranslation() {
    setCreating(true);
    setError(null);

    try {
      const createdResult = await translatePullRequestDraft(draftId);
      setTranslateResult(createdResult);

      const loadedTranslations = await getPullRequestDraftTranslations(draftId);
      setTranslations(loadedTranslations);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "PR 초안 번역 생성에 실패했습니다.");
    } finally {
      setCreating(false);
      setLoading(false);
    }
  }

  async function copyTranslation(translation: PullRequestDraftTranslation) {
    await navigator.clipboard.writeText(`${translation.title}\n\n${translation.body}`);
    setCopiedId(translation.id);
    window.setTimeout(() => setCopiedId(null), 1600);
  }

  return (
    <section className="rounded-md border border-github-border p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold">English Draft</h3>
          <p className="mt-1 text-xs font-semibold text-github-muted">
            {translations.length} translations
          </p>
        </div>
        <button
          className="focus-ring rounded-md bg-github-accent px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          disabled={creating}
          onClick={handleCreateTranslation}
          type="button"
        >
          {creating ? "생성 중..." : "영문 초안 생성"}
        </button>
      </div>

      {error ? (
        <div className="mt-4 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
          {error}
        </div>
      ) : null}

      {loading ? (
        <p className="mt-4 text-sm font-semibold text-github-muted">불러오는 중...</p>
      ) : null}

      {translateResult ? (
        <article className="mt-4 rounded-md border border-github-accent/30 bg-github-accent/5 p-3">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs font-semibold uppercase text-github-accent">latest translate action</p>
            <a
              className="focus-ring rounded-md bg-github-accent px-2 py-1 text-xs font-semibold text-white"
              href={translateResult.githubUrl}
              rel="noreferrer"
              target="_blank"
            >
              GitHub PR 작성
            </a>
          </div>
          <p className="mt-3 text-sm font-semibold leading-6 text-github-text">{translateResult.titleEn}</p>
          <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap text-sm leading-6 text-github-text">
            {translateResult.bodyEn}
          </pre>
        </article>
      ) : null}

      {!loading && translations.length === 0 ? (
        <p className="mt-4 text-sm font-semibold text-github-muted">저장된 영문 초안이 없습니다.</p>
      ) : null}

      <div className="mt-4 space-y-3">
        {translations.map((translation) => (
          <article className="rounded-md bg-github-bg p-3" key={translation.id}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs font-semibold uppercase text-github-accent">{translation.targetLanguage}</p>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-semibold text-github-muted">
                  {formatDate(translation.createdAt)}
                </span>
                <button
                  className="focus-ring rounded-md border border-github-border bg-github-surface px-2 py-1 text-xs font-semibold text-github-muted"
                  onClick={() => copyTranslation(translation)}
                  type="button"
                >
                  {copiedId === translation.id ? "복사됨" : "복사"}
                </button>
              </div>
            </div>
            <p className="mt-3 text-sm font-semibold leading-6 text-github-text">{translation.title}</p>
            <pre className="mt-2 max-h-80 overflow-auto whitespace-pre-wrap text-sm leading-6 text-github-text">
              {translation.body}
            </pre>
            <TranslationTimelinePanel draftId={draftId} translation={translation} />
            <TranslationReadinessPanel draftId={draftId} translation={translation} />
            <TranslationSubmissionBundle draftId={draftId} translation={translation} />
          </article>
        ))}
      </div>
    </section>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
