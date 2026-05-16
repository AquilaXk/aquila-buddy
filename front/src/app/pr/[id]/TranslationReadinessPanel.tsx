"use client";

import { useEffect, useState } from "react";
import {
  getPullRequestDraftTranslationReadiness,
  type PullRequestDraftReadinessStatus,
  type PullRequestDraftTranslation,
  type PullRequestDraftTranslationReadiness
} from "@/lib/api";

type TranslationReadinessPanelProps = {
  draftId: string;
  translation: PullRequestDraftTranslation;
};

const statusLabels: Record<PullRequestDraftReadinessStatus, string> = {
  READY: "준비됨",
  ACTION_REQUIRED: "확인 필요"
};

const statusClassNames: Record<PullRequestDraftReadinessStatus, string> = {
  READY: "border-moss bg-white text-moss",
  ACTION_REQUIRED: "border-red-200 bg-red-50 text-red-700"
};

export function TranslationReadinessPanel({ draftId, translation }: TranslationReadinessPanelProps) {
  const [readiness, setReadiness] = useState<PullRequestDraftTranslationReadiness | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadReadiness() {
      setLoading(true);
      try {
        const loadedReadiness = await getPullRequestDraftTranslationReadiness(draftId, translation.id);
        if (mounted) {
          setReadiness(loadedReadiness);
          setError(null);
        }
      } catch (caught) {
        if (mounted) {
          setError(caught instanceof Error ? caught.message : "영문 readiness를 불러오지 못했습니다.");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    loadReadiness();

    return () => {
      mounted = false;
    };
  }, [draftId, translation.id]);

  if (loading) {
    return <p className="mt-4 text-sm font-semibold text-neutral-600">영문 readiness를 불러오는 중...</p>;
  }

  if (error) {
    return (
      <div className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
        {error}
      </div>
    );
  }

  if (!readiness) {
    return (
      <p className="mt-4 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-neutral-600">
        영문 readiness가 준비되지 않았습니다.
      </p>
    );
  }

  const totalCount = readiness.items.length;

  return (
    <div className="mt-4 rounded-md border border-line bg-white p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase text-moss">English Readiness</p>
          <h4 className="mt-1 text-sm font-semibold text-ink">
            {readiness.readyCount} / {totalCount} 항목 준비됨
          </h4>
        </div>
        <span className="max-w-full break-all rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-700">
          {readiness.targetLanguage} / {readiness.translationId}
        </span>
      </div>

      <ol className="mt-3 divide-y divide-line">
        {readiness.items.map((item, index) => (
          <li className="grid gap-3 py-3 first:pt-0 last:pb-0 md:grid-cols-[2rem_1fr]" key={item.key}>
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-neutral-100 text-sm font-bold text-moss">
              {index + 1}
            </div>
            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h5 className="text-sm font-semibold text-ink">{item.label}</h5>
                <span
                  className={`rounded-md border px-2 py-1 text-xs font-semibold ${statusClassNames[item.status]}`}
                >
                  {statusLabels[item.status]}
                </span>
              </div>
              <p className="mt-2 text-sm leading-6 text-neutral-700">{item.description}</p>
              <p className="mt-2 break-words rounded-md bg-neutral-100 px-3 py-2 text-sm leading-6 text-neutral-800">
                {item.detail}
              </p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}
