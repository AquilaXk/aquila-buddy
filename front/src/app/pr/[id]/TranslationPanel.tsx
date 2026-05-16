"use client";

import { useEffect, useState } from "react";
import {
  createPullRequestDraftTranslation,
  getPullRequestDraftTranslations,
  type PullRequestDraftTranslation
} from "@/lib/api";
import { TranslationSubmissionBundle } from "./TranslationSubmissionBundle";

type TranslationPanelProps = {
  draftId: string;
};

export function TranslationPanel({ draftId }: TranslationPanelProps) {
  const [translations, setTranslations] = useState<PullRequestDraftTranslation[]>([]);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

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
      const createdTranslation = await createPullRequestDraftTranslation(draftId, "en");
      setTranslations((currentTranslations) => [
        createdTranslation,
        ...currentTranslations.filter((translation) => translation.id !== createdTranslation.id)
      ]);
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
    <section className="rounded-md border border-line p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h3 className="text-sm font-semibold">English Draft</h3>
          <p className="mt-1 text-xs font-semibold text-neutral-500">
            {translations.length} translations
          </p>
        </div>
        <button
          className="focus-ring rounded-md bg-moss px-3 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          disabled={creating}
          onClick={handleCreateTranslation}
          type="button"
        >
          {creating ? "생성 중..." : "영문 초안 생성"}
        </button>
      </div>

      {error ? (
        <div className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
          {error}
        </div>
      ) : null}

      {loading ? (
        <p className="mt-4 text-sm font-semibold text-neutral-600">불러오는 중...</p>
      ) : null}

      {!loading && translations.length === 0 ? (
        <p className="mt-4 text-sm font-semibold text-neutral-600">저장된 영문 초안이 없습니다.</p>
      ) : null}

      <div className="mt-4 space-y-3">
        {translations.map((translation) => (
          <article className="rounded-md bg-neutral-100 p-3" key={translation.id}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-xs font-semibold uppercase text-moss">{translation.targetLanguage}</p>
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-xs font-semibold text-neutral-500">
                  {formatDate(translation.createdAt)}
                </span>
                <button
                  className="focus-ring rounded-md border border-line bg-white px-2 py-1 text-xs font-semibold text-neutral-700"
                  onClick={() => copyTranslation(translation)}
                  type="button"
                >
                  {copiedId === translation.id ? "복사됨" : "복사"}
                </button>
              </div>
            </div>
            <p className="mt-3 text-sm font-semibold leading-6 text-ink">{translation.title}</p>
            <pre className="mt-2 max-h-80 overflow-auto whitespace-pre-wrap text-sm leading-6 text-neutral-800">
              {translation.body}
            </pre>
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
