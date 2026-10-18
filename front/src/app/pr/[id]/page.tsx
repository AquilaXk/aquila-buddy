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
import { formatDate } from "@/lib/format";

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
          <div className="surface h-16 animate-pulse bg-neutral-subtle" />
          <div className="surface h-80 animate-pulse bg-neutral-subtle" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !draft) {
    return (
      <ProtectedLayout>
        <section className="surface py-16 text-center">
          <h1 className="text-lg font-semibold text-ink-primary">PR 초안을 찾을 수 없습니다.</h1>
          <p className="mt-2 text-xs text-ink-secondary">
            저장된 PR 초안 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-brand-500 hover:text-brand-600"
            href="/pr-draft"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>PR 초안 목록</span>
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
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-neutral-border pb-4">
        <div className="flex flex-wrap items-center gap-2.5 text-xs">
          <Link
            className="focus-ring inline-flex items-center gap-1 font-medium text-ink-secondary transition-colors hover:text-ink-primary"
            href="/pr-draft"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>PR 초안 목록</span>
          </Link>
          <span className="text-neutral-borderStrong">/</span>
          <span className="font-semibold text-ink-primary">연결된 이슈</span>
          <span className="font-mono font-medium text-ink-primary">
            {draft.upstreamRepo} #{draft.githubIssueNumber}
          </span>
          <span className="rounded border border-neutral-border bg-neutral-subtle px-2 py-0.5 font-mono text-[11px] text-ink-secondary">
            {draft.baseBranch} → {draft.headBranch}
          </span>
          <span className="tabular-nums font-mono text-ink-muted">{formatDate(draft.createdAt)}</span>
          {draft.sourceIssueId ? (
            <Link
              className="focus-ring inline-flex items-center gap-1 font-medium text-brand-500 hover:text-brand-600"
              href={`/issues/${encodeURIComponent(draft.sourceIssueId)}`}
            >
              <span>원본 이슈 보기</span>
              <ExternalLink className="h-3 w-3" />
            </Link>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {!editing ? (
            <button
              className="btn-secondary focus-ring inline-flex items-center gap-1.5 px-3 py-1.5 text-xs"
              onClick={() => {
                setActiveLangTab("ko");
                setEditing(true);
              }}
              type="button"
            >
              <Edit2 className="h-3.5 w-3.5" />
              <span className="button-text">편집</span>
            </button>
          ) : null}
          <button
            className="focus-ring inline-flex items-center gap-1.5 rounded border border-[#c5221f]/20 bg-[#fce8e6] px-3 py-1.5 text-xs font-medium text-[#c5221f] transition-colors hover:bg-[#fad2cf] disabled:cursor-not-allowed disabled:opacity-50"
            disabled={deleting}
            onClick={handleDelete}
            type="button"
          >
            <Trash2 className="h-3.5 w-3.5" />
            <span className="button-text">{deleting ? "삭제 중..." : "삭제"}</span>
          </button>
        </div>
      </header>

      {error ? (
        <div className="mb-4 rounded border border-[#c5221f]/20 bg-[#fce8e6] px-4 py-3 text-xs font-medium text-[#c5221f]">
          {error}
        </div>
      ) : null}

      {translated ? (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded border border-brand-500/20 bg-brand-50 px-4 py-2.5 text-xs font-medium text-brand-700">
          <span>영어 번역이 완료되었습니다. 아래 내용으로 GitHub PR을 작성하세요.</span>
          <a
            className="font-semibold underline hover:text-brand-800"
            href={translated.githubUrl}
            rel="noreferrer"
            target="_blank"
          >
            GitHub에서 PR 올리기 ↗
          </a>
        </div>
      ) : null}

      <section className="surface mb-6 divide-y divide-neutral-border overflow-hidden">
        <div className="sheet-header flex flex-wrap items-center justify-between gap-3 px-5 py-3">
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

          {editing ? (
            <div className="flex items-center gap-2">
              <button
                className="btn-primary focus-ring inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                disabled={saving}
                onClick={handleSave}
                type="button"
              >
                <Save className="h-3.5 w-3.5" />
                <span className="button-text">{saving ? "저장 중..." : "저장"}</span>
              </button>
              <button
                className="btn-secondary focus-ring inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs"
                onClick={cancelEdit}
                type="button"
              >
                <X className="h-3.5 w-3.5" />
                <span className="button-text">취소</span>
              </button>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              {translated ? (
                <a
                  className="btn-primary focus-ring inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs"
                  href={translated.githubUrl}
                  rel="noreferrer"
                  target="_blank"
                >
                  <ExternalLink className="h-3.5 w-3.5" />
                  <span className="button-text">GitHub에서 PR 올리기 ↗</span>
                </a>
              ) : (
                <button
                  className="btn-primary focus-ring inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={translating}
                  onClick={handleTranslate}
                  type="button"
                >
                  {translating ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span className="button-text">번역 중...</span>
                    </>
                  ) : (
                    <>
                      <Globe className="h-3.5 w-3.5" />
                      <span className="button-text">영어로 번역하기</span>
                    </>
                  )}
                </button>
              )}
            </div>
          )}
        </div>

        <div className="px-5 py-4">
          <div className="mb-1.5 flex items-center justify-between gap-3">
            <span className="text-xs font-semibold text-ink-secondary">PR 제목</span>
            <button
              className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-ink-secondary transition-colors hover:text-brand-500"
              onClick={() => copyToClipboard(displayTitle, "title")}
              type="button"
            >
              {copied === "title" ? (
                <>
                  <Check className="h-3 w-3 text-[#137333]" />
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
              className="focus-ring w-full rounded border border-neutral-border bg-white px-3 py-1.5 text-sm text-ink-primary focus:border-brand-500"
              onChange={(event) => setEditTitle(event.target.value)}
              value={editTitle}
            />
          ) : (
            <p className="text-sm font-medium leading-6 text-ink-primary">{displayTitle}</p>
          )}
        </div>

        <div className="px-5 py-4">
          <div className="mb-2 flex items-center justify-between gap-3">
            <span className="text-xs font-semibold text-ink-secondary">PR 본문</span>
            <button
              className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-ink-secondary transition-colors hover:text-brand-500"
              onClick={() => copyToClipboard(displayBody, "body")}
              type="button"
            >
              {copied === "body" ? (
                <>
                  <Check className="h-3 w-3 text-[#137333]" />
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
              className="focus-ring min-h-72 w-full resize-y rounded border border-neutral-border bg-white px-3.5 py-2.5 font-mono text-xs leading-6 text-ink-primary focus:border-brand-500"
              onChange={(event) => setEditBody(event.target.value)}
              value={editBody}
            />
          ) : (
            <pre className="max-h-72 overflow-y-auto whitespace-pre-wrap rounded border border-neutral-border bg-neutral-subtle p-4 font-mono text-xs leading-6 text-ink-primary">
              {displayBody}
            </pre>
          )}
        </div>

        {draft.diffContent ? (
          <div className="px-5 py-4">
            <p className="mb-2 text-xs font-semibold text-ink-secondary">코드 변경 내용 (diff)</p>
            <pre className="max-h-56 overflow-y-auto whitespace-pre-wrap rounded border border-neutral-border bg-neutral-subtle p-4 font-mono text-xs leading-6 text-ink-primary">
              {draft.diffContent.length > 3000
                ? `${draft.diffContent.slice(0, 3000)}\n... (생략)`
                : draft.diffContent}
            </pre>
          </div>
        ) : null}
      </section>

      <PrDraftReadinessSection readiness={readiness} />
    </ProtectedLayout>
  );
}
