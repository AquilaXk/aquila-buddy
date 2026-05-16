"use client";

import { useEffect, useState } from "react";
import {
  getPullRequestDraftTranslationTimeline,
  type PullRequestDraftTimelineStatus,
  type PullRequestDraftTranslation,
  type PullRequestDraftTranslationTimeline
} from "@/lib/api";

type TranslationTimelinePanelProps = {
  draftId: string;
  translation: PullRequestDraftTranslation;
};

const statusLabels: Record<PullRequestDraftTimelineStatus, string> = {
  DONE: "완료",
  READY: "준비됨",
  NEEDS_ATTENTION: "확인 필요"
};

const statusClassNames: Record<PullRequestDraftTimelineStatus, string> = {
  DONE: "border-moss bg-white text-moss",
  READY: "border-line bg-neutral-50 text-neutral-700",
  NEEDS_ATTENTION: "border-red-200 bg-red-50 text-red-700"
};

export function TranslationTimelinePanel({ draftId, translation }: TranslationTimelinePanelProps) {
  const [timeline, setTimeline] = useState<PullRequestDraftTranslationTimeline | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadTimeline() {
      setLoading(true);
      try {
        const loadedTimeline = await getPullRequestDraftTranslationTimeline(draftId, translation.id);
        if (mounted) {
          setTimeline(loadedTimeline);
          setError(null);
        }
      } catch (caught) {
        if (mounted) {
          setError(caught instanceof Error ? caught.message : "영문 timeline을 불러오지 못했습니다.");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    }

    loadTimeline();

    return () => {
      mounted = false;
    };
  }, [draftId, translation.id]);

  if (loading) {
    return <p className="mt-4 text-sm font-semibold text-neutral-600">영문 timeline을 불러오는 중...</p>;
  }

  if (error) {
    return (
      <div className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
        {error}
      </div>
    );
  }

  if (!timeline) {
    return (
      <p className="mt-4 rounded-md border border-line bg-white px-3 py-2 text-sm font-semibold text-neutral-600">
        영문 timeline이 준비되지 않았습니다.
      </p>
    );
  }

  return (
    <div className="mt-4 rounded-md border border-line bg-white p-3">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase text-moss">English Timeline</p>
          <h4 className="mt-1 text-sm font-semibold text-ink">
            {timeline.items.length}개 번역 준비 단계
          </h4>
        </div>
        <span className="max-w-full break-all rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-700">
          {timeline.targetLanguage} / {timeline.translationId}
        </span>
      </div>

      <ol className="mt-3 divide-y divide-line">
        {timeline.items.map((item, index) => (
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
              <p className="mt-2 text-xs font-semibold text-neutral-500">{formatDate(item.occurredAt)}</p>
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
