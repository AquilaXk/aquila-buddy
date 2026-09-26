"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import {
  ArrowLeft,
  Copy,
  Check,
  Edit2,
  Trash2,
  Globe,
  ExternalLink,
  Save,
  X,
  Loader2
} from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { PrDraftReadinessSection } from "@/components/PrDraftReadinessSection";
import { LanguageTabSwitcher } from "@/components/LanguageTabSwitcher";
import {
  deletePullRequestDraft,
  getPullRequestDraft,
  getPullRequestDraftReadiness,
  getPullRequestDraftTranslations,
  translatePullRequestDraft,
  updatePullRequestDraft,
  type PullRequestDraftReadiness,
  type PullRequestDraftTranslateResult,
  type StoredPullRequestDraft
} from "@/lib/api";

export default function PrDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const prId = params.id;
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [readiness, setReadiness] = useState<PullRequestDraftReadiness | null>(null);
  const [activeLangTab, setActiveLangTab] = useState<"ko" | "en">("ko");
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editBody, setEditBody] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [translating, setTranslating] = useState(false);
  const [translated, setTranslated] = useState<PullRequestDraftTranslateResult | null>(null);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadDraft() {
      setLoading(true);
      setNotFound(false);

      try {
        const loadedDraft = await getPullRequestDraft(prId);
        if (!isMounted) {
          return;
        }

        setDraft(loadedDraft);
        setEditTitle(loadedDraft.title);
        setEditBody(loadedDraft.body);

        try {
          const loadedReadiness = await getPullRequestDraftReadiness(prId);
          if (isMounted) {
            setReadiness(loadedReadiness);
          }
        } catch {
          if (isMounted) {
            setReadiness(null);
          }
        }

        try {
          const loadedTranslations = await getPullRequestDraftTranslations(prId);
          if (isMounted && loadedTranslations.length > 0) {
            const latest = loadedTranslations[loadedTranslations.length - 1];
            setTranslated({
              titleEn: latest.title,
              bodyEn: latest.body,
              sourceIssueId: latest.sourceIssueId,
              githubUrl: `https://github.com/${loadedDraft.upstreamRepo}/compare/${encodeURIComponent(loadedDraft.baseBranch)}...${encodeURIComponent(loadedDraft.headBranch)}?expand=1&title=${encodeURIComponent(latest.title)}&body=${encodeURIComponent(latest.body)}`
            });
          }
        } catch {
          // 저장된 번역이 없는 경우 무시하고 원본 초안으로 진행합니다.
        }
      } catch {
        if (!isMounted) {
          return;
        }

        setDraft(null);
        setReadiness(null);
        setNotFound(true);
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    }

    loadDraft();

    return () => {
      isMounted = false;
    };
  }, [prId]);

  async function handleSave() {
    if (!draft) {
      return;
    }

    setSaving(true);
    setError(null);

    try {
      const updatedDraft = await updatePullRequestDraft(prId, {
        title: editTitle,
        body: editBody
      });
      setDraft(updatedDraft);
      setTranslated(null);
      setActiveLangTab("ko");
      setEditing(false);

      getPullRequestDraftReadiness(prId)
        .then(setReadiness)
        .catch(() => setReadiness(null));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("PR 초안을 삭제하시겠습니까?")) {
      return;
    }

    setDeleting(true);
    setError(null);

    try {
      await deletePullRequestDraft(prId);
      router.replace("/pr-draft");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "삭제에 실패했습니다.");
      setDeleting(false);
    }
  }

  async function handleTranslate() {
    setTranslating(true);
    setError(null);

    try {
      const result = await translatePullRequestDraft(prId);
      setTranslated(result);
      setActiveLangTab("en");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "번역에 실패했습니다.");
    } finally {
      setTranslating(false);
    }
  }

  async function copyToClipboard(text: string, type: "title" | "body") {
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      }
      setCopied(type);
      window.setTimeout(() => setCopied(null), 2000);
    } catch {
      // clipboard access fallback
    }
  }

  function cancelEdit() {
    if (!draft) {
      return;
    }

    setEditing(false);
    setEditTitle(draft.title);
    setEditBody(draft.body);
  }

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="surface rounded-xl h-20 animate-pulse" />
          <div className="surface rounded-xl h-28 animate-pulse" />
          <div className="surface rounded-xl h-40 animate-pulse" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !draft) {
    return (
      <ProtectedLayout>
        <section className="surface rounded-xl py-20 text-center">
          <h1 className="text-2xl font-bold text-dark-text">PR 초안을 찾을 수 없습니다.</h1>
          <p className="mt-3 text-sm font-semibold text-dark-muted">
            저장된 PR 초안 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-6 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-400 hover:text-brand-300 transition-colors"
            href="/pr-draft"
          >
            ← PR 초안 목록으로
          </Link>
        </section>
      </ProtectedLayout>
    );
  }

  const isEnglishTab = activeLangTab === "en";
  const displayTitle = isEnglishTab && translated ? translated.titleEn : draft.title;
  const displayBody = isEnglishTab && translated ? translated.bodyEn : draft.body;

  return (
    <ProtectedLayout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <Link
          className="focus-ring inline-flex items-center gap-1.5 text-sm font-semibold text-dark-muted hover:text-dark-text transition-colors"
          href="/pr-draft"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>← PR 초안 목록</span>
        </Link>
        <div className="flex items-center gap-2">
          {!editing ? (
            <button
              className="focus-ring inline-flex items-center gap-1.5 rounded-xl border border-dark-border bg-dark-surface px-3.5 py-1.5 text-xs font-semibold text-dark-muted hover:text-dark-text hover:bg-dark-elevated transition-colors"
              onClick={() => {
                setActiveLangTab("ko");
                setEditing(true);
              }}
              type="button"
            >
              <Edit2 className="h-3.5 w-3.5" />
              <span>편집</span>
            </button>
          ) : null}
          <button
            className="focus-ring inline-flex items-center gap-1.5 rounded-xl border border-red-500/20 bg-red-500/10 px-3.5 py-1.5 text-xs font-semibold text-red-400 hover:bg-red-500/20 hover:text-red-300 disabled:cursor-not-allowed disabled:opacity-50 transition-colors"
            disabled={deleting}
            onClick={handleDelete}
            type="button"
          >
            <Trash2 className="h-3.5 w-3.5" />
            <span>{deleting ? "삭제 중..." : "삭제"}</span>
          </button>
        </div>
      </div>

      {error ? (
        <div className="surface mb-4 rounded-xl border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          {error}
        </div>
      ) : null}

      {translated ? (
        <div className="surface mb-4 rounded-xl border-brand-500/40 bg-brand-500/10 p-4 text-sm font-semibold text-brand-300">
          영어 번역이 완료되었습니다. 아래 내용으로 GitHub PR을 작성하세요.
        </div>
      ) : null}

      <section className="surface mb-5 flex flex-col gap-3 rounded-xl px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-brand-400">연결된 이슈</p>
          <p className="mt-1 font-mono text-sm font-semibold text-dark-text">
            {draft.upstreamRepo} #{draft.githubIssueNumber}
          </p>
          <p className="mt-1 break-all font-mono text-xs text-dark-muted">
            {draft.baseBranch} -&gt; {draft.headBranch}
          </p>
        </div>
        <div className="flex flex-col items-start gap-2 sm:items-end">
          <p className="text-xs font-semibold text-dark-muted">{formatDate(draft.createdAt)}</p>
          {draft.sourceIssueId ? (
            <Link
              className="focus-ring inline-flex items-center gap-1 text-xs font-semibold text-brand-400 hover:text-brand-300 transition-colors"
              href={`/issues/${encodeURIComponent(draft.sourceIssueId)}`}
            >
              <span>원본 이슈 보기</span>
              <ExternalLink className="h-3 w-3" />
            </Link>
          ) : null}
        </div>
      </section>

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-dark-text">PR 초안 내용</h2>
        <LanguageTabSwitcher
          activeTab={activeLangTab}
          disabled={editing}
          hasTranslation={Boolean(translated)}
          onSelectTab={(tab) => {
            setActiveLangTab(tab);
            if (tab === "en" && !translated && !translating) {
              handleTranslate();
            }
          }}
        />
      </div>

      <section className="surface mb-4 rounded-xl p-5">
        <div className="mb-2 flex items-center justify-between gap-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-dark-muted">PR 제목</p>
          <button
            className="focus-ring inline-flex items-center gap-1 text-xs font-semibold text-dark-muted hover:text-brand-400 transition-colors"
            onClick={() => copyToClipboard(displayTitle, "title")}
            type="button"
          >
            {copied === "title" ? (
              <>
                <Check className="h-3 w-3 text-emerald-400" />
                <span>복사됨 ✓</span>
              </>
            ) : (
              <>
                <Copy className="h-3 w-3" />
                <span>복사</span>
              </>
            )}
          </button>
        </div>
        {editing ? (
          <input
            className="focus-ring w-full rounded-xl border border-dark-border bg-dark-scaffold px-3.5 py-2 text-sm text-dark-text focus:border-brand-500"
            onChange={(event) => setEditTitle(event.target.value)}
            value={editTitle}
          />
        ) : (
          <p className="text-sm font-semibold leading-6 text-dark-text">{displayTitle}</p>
        )}
      </section>

      <section className="surface mb-4 rounded-xl p-5">
        <div className="mb-2 flex items-center justify-between gap-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-dark-muted">PR 본문</p>
          <button
            className="focus-ring inline-flex items-center gap-1 text-xs font-semibold text-dark-muted hover:text-brand-400 transition-colors"
            onClick={() => copyToClipboard(displayBody, "body")}
            type="button"
          >
            {copied === "body" ? (
              <>
                <Check className="h-3 w-3 text-emerald-400" />
                <span>복사됨 ✓</span>
              </>
            ) : (
              <>
                <Copy className="h-3 w-3" />
                <span>복사</span>
              </>
            )}
          </button>
        </div>
        {editing ? (
          <textarea
            className="focus-ring min-h-72 w-full resize-y rounded-xl border border-dark-border bg-dark-scaffold px-3.5 py-2.5 font-mono text-xs leading-6 text-dark-text focus:border-brand-500"
            onChange={(event) => setEditBody(event.target.value)}
            value={editBody}
          />
        ) : (
          <pre className="max-h-72 overflow-y-auto whitespace-pre-wrap font-mono text-xs leading-6 text-dark-text">
            {displayBody}
          </pre>
        )}
      </section>

      {draft.diffContent ? (
        <section className="surface mb-5 rounded-xl p-5">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-dark-muted">코드 변경 내용 (diff)</p>
          <pre className="max-h-48 overflow-y-auto whitespace-pre-wrap rounded-xl border border-dark-border bg-dark-scaffold p-4 font-mono text-xs leading-6 text-dark-text">
            {draft.diffContent.length > 3000
              ? `${draft.diffContent.slice(0, 3000)}\n... (생략)`
              : draft.diffContent}
          </pre>
        </section>
      ) : null}

      <PrDraftReadinessSection readiness={readiness} />

      {editing ? (
        <div className="flex flex-wrap gap-3">
          <button
            className="focus-ring inline-flex items-center gap-1.5 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 active:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 transition-all"
            disabled={saving}
            onClick={handleSave}
            type="button"
          >
            <Save className="h-4 w-4" />
            <span>{saving ? "저장 중..." : "저장"}</span>
          </button>
          <button
            className="focus-ring inline-flex items-center gap-1.5 rounded-xl border border-dark-border bg-dark-surface px-5 py-2.5 text-sm font-semibold text-dark-muted hover:text-dark-text hover:bg-dark-elevated transition-colors"
            onClick={cancelEdit}
            type="button"
          >
            <X className="h-4 w-4" />
            <span>취소</span>
          </button>
        </div>
      ) : (
        <div className="flex flex-wrap gap-3">
          {translated ? (
            <a
              className="focus-ring inline-flex items-center gap-2 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 active:bg-brand-700 transition-all"
              href={translated.githubUrl}
              rel="noreferrer"
              target="_blank"
            >
              <ExternalLink className="h-4 w-4" />
              <span>GitHub에서 PR 올리기 ↗</span>
            </a>
          ) : (
            <button
              className="focus-ring inline-flex items-center gap-2 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 active:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50 transition-all"
              disabled={translating}
              onClick={handleTranslate}
              type="button"
            >
              {translating ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin" />
                  <span>번역 중...</span>
                </>
              ) : (
                <>
                  <Globe className="h-4 w-4" />
                  <span>영어로 번역하기</span>
                </>
              )}
            </button>
          )}
        </div>
      )}
    </ProtectedLayout>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
