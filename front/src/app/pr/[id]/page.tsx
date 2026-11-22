"use client";

import { Suspense, useCallback, useEffect, useId, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import {
  AlertTriangle,
  ArrowLeft,
  Check,
  Copy,
  Edit2,
  ExternalLink,
  Globe,
  Loader2,
  Save,
  Trash2,
  X,
} from "@/components/icons";
import { DiffViewer } from "@/components/DiffViewer";
import { ExternalBrowserLink } from "@/components/ExternalBrowserLink";
import { LanguageTabSwitcher } from "@/components/LanguageTabSwitcher";
import { MarkdownPreview } from "@/components/MarkdownPreview";
import { PrDraftReadinessSection } from "@/components/PrDraftReadinessSection";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StudioSplitPane } from "@/components/StudioSplitPane";
import {
  deletePullRequestDraft,
  getIssueGuide,
  getPullRequestDraft,
  getPullRequestDraftReadiness,
  getPullRequestDraftTranslations,
  translatePullRequestDraft,
  updatePullRequestDraft,
  type PullRequestDraftReadiness,
  type PullRequestDraftTranslateResult,
  type StoredPullRequestDraft,
} from "@/lib/api";
import { formatDate } from "@/lib/format";
import { buildSafeGitHubCompareUrl } from "@/lib/githubQuickPullUrl";
import { issueDetailHref, resolveDynamicRouteId } from "@/lib/routes";

export default function PrDetailPage() {
  return (
    <Suspense fallback={null}>
      <PrDetailContent />
    </Suspense>
  );
}

function PrDetailContent() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const prId = resolveDynamicRouteId(params.id, searchParams.get("id"));
  const langIdPrefix = useId();
  const deleteTitleId = useId();
  const deleteDescId = useId();
  const deleteDialogRef = useRef<HTMLDialogElement>(null);
  const cancelDeleteBtnRef = useRef<HTMLButtonElement>(null);

  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [readiness, setReadiness] = useState<PullRequestDraftReadiness | null>(null);
  const [issueFilePaths, setIssueFilePaths] = useState<string[]>([]);
  const [activeLangTab, setActiveLangTab] = useState<"ko" | "en">("ko");
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [editing, setEditing] = useState(false);
  const [bodyViewMode, setBodyViewMode] = useState<"preview" | "raw">("preview");
  const [editTitle, setEditTitle] = useState("");
  const [editBody, setEditBody] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [translating, setTranslating] = useState(false);
  const [translated, setTranslated] = useState<PullRequestDraftTranslateResult | null>(null);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const closeDeleteDialog = useCallback(() => {
    const dialog = deleteDialogRef.current;
    if (dialog?.open) {
      dialog.close();
    }
  }, []);

  const openDeleteDialog = useCallback(() => {
    const dialog = deleteDialogRef.current;
    if (!dialog) {
      return;
    }
    if (!dialog.open) {
      dialog.showModal();
    }
    cancelDeleteBtnRef.current?.focus();
  }, []);

  useEffect(() => {
    const dialog = deleteDialogRef.current;
    if (!dialog) {
      return;
    }

    function handleClose() {
      if (dialog) {
        dialog.returnValue = "";
      }
    }

    dialog.addEventListener("close", handleClose);

    if (typeof HTMLDialogElement !== "undefined" && !("closedBy" in HTMLDialogElement.prototype)) {
      let pressedOutside = false;
      const isOutside = (e: PointerEvent) => {
        const rect = dialog.getBoundingClientRect();
        return (
          e.clientX < rect.left ||
          e.clientX > rect.right ||
          e.clientY < rect.top ||
          e.clientY > rect.bottom
        );
      };
      const onPointerDown = (e: PointerEvent) => {
        pressedOutside = isOutside(e);
      };
      const onPointerUp = (e: PointerEvent) => {
        if (pressedOutside && isOutside(e)) {
          closeDeleteDialog();
        }
        pressedOutside = false;
      };
      dialog.addEventListener("pointerdown", onPointerDown);
      dialog.addEventListener("pointerup", onPointerUp);
      return () => {
        dialog.removeEventListener("close", handleClose);
        dialog.removeEventListener("pointerdown", onPointerDown);
        dialog.removeEventListener("pointerup", onPointerUp);
      };
    }

    return () => {
      dialog.removeEventListener("close", handleClose);
    };
  }, [closeDeleteDialog]);

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

        if (loadedDraft.sourceIssueId) {
          getIssueGuide(loadedDraft.sourceIssueId)
            .then((guide) => {
              if (isMounted && Array.isArray(guide?.filePaths)) {
                setIssueFilePaths(guide.filePaths);
              }
            })
            .catch(() => {
              if (isMounted) {
                setIssueFilePaths([]);
              }
            });
        } else {
          setIssueFilePaths([]);
        }

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
            const safeCompare = buildSafeGitHubCompareUrl({
              upstreamRepo: loadedDraft.upstreamRepo,
              baseBranch: loadedDraft.baseBranch,
              headBranch: loadedDraft.headBranch,
              title: latest.title,
              body: latest.body,
            });
            setTranslated({
              titleEn: latest.title,
              bodyEn: latest.body,
              sourceIssueId: latest.sourceIssueId,
              githubUrl: safeCompare.url,
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
        body: editBody,
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

  async function confirmDelete() {
    setDeleting(true);
    setError(null);
    closeDeleteDialog();

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

  const safeTranslatedCompare = useMemo(() => {
    if (!translated || !draft) {
      return null;
    }
    return buildSafeGitHubCompareUrl({
      upstreamRepo: draft.upstreamRepo,
      baseBranch: draft.baseBranch,
      headBranch: draft.headBranch,
      title: translated.titleEn,
      body: translated.bodyEn,
    });
  }, [draft, translated]);

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="h-16 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
          <div className="h-80 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !draft) {
    return (
      <ProtectedLayout>
        <section className="primer-box py-16 text-center">
          <h1 className="text-[20px] font-semibold text-[#1f2328]">PR 초안을 찾을 수 없습니다.</h1>
          <p className="mt-2 text-[13.5px] text-[#59636e]">
            저장된 PR 초안 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-[#0969da] hover:underline"
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
  const koTabId = `${langIdPrefix}-tab-ko`;
  const enTabId = `${langIdPrefix}-tab-en`;
  const draftPanelId = `${langIdPrefix}-panel`;

  const leftDraftPane = (
    <section className="primer-box divide-y divide-[#d1d9e0] overflow-hidden">
      <div className="primer-box-header flex flex-wrap items-center justify-between gap-3 px-5 py-3">
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
          panelIds={{ ko: draftPanelId, en: draftPanelId }}
          tabIds={{ ko: koTabId, en: enTabId }}
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
              <ExternalBrowserLink
                className="btn-primer-green focus-ring inline-flex items-center gap-1.5 px-3.5 py-1.5 text-xs"
                href={safeTranslatedCompare?.url ?? translated.githubUrl}
                rel="noreferrer"
              >
                <ExternalLink className="h-3.5 w-3.5" />
                <span className="button-text">GitHub에서 PR 올리기 ↗</span>
              </ExternalBrowserLink>
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

      <div
        aria-labelledby={isEnglishTab ? enTabId : koTabId}
        className="divide-y divide-[#d1d9e0]"
        id={draftPanelId}
        role="tabpanel"
      >
        <div className="px-5 py-4">
          <div className="mb-1.5 flex items-center justify-between gap-3">
            <span className="text-xs font-semibold text-[#59636e]">PR 제목</span>
            <button
              className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
              onClick={() => copyToClipboard(displayTitle, "title")}
              type="button"
            >
              {copied === "title" ? (
                <>
                  <Check className="h-3.5 w-3.5 text-[#1a7f37]" />
                  <span>복사됨 ✓</span>
                </>
              ) : (
                <>
                  <Copy className="h-3.5 w-3.5" />
                  <span>복사</span>
                </>
              )}
            </button>
          </div>
          {editing ? (
            <input
              aria-label="PR 제목 편집"
              className="focus-ring w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 text-[14px] text-[#1f2328] focus:border-[#0969da]"
              onChange={(event) => setEditTitle(event.target.value)}
              value={editTitle}
            />
          ) : (
            <p className="text-[15px] font-semibold leading-[1.45] text-[#1f2328]">{displayTitle}</p>
          )}
        </div>

        <div className="px-5 py-4">
          <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
            <span className="text-xs font-semibold text-[#59636e]">PR 본문</span>
            <div className="flex items-center gap-2">
              {!editing ? (
                <div
                  aria-label="본문 보기 형식"
                  className="flex items-center gap-0.5 rounded-md border border-[#d1d9e0] bg-[#eaeef2] p-0.5"
                  role="group"
                >
                  <button
                    aria-pressed={bodyViewMode === "preview"}
                    className={
                      bodyViewMode === "preview"
                        ? "focus-ring rounded-[5px] border border-[#d1d9e0] bg-white px-2 py-0.5 text-[11px] font-semibold text-[#1f2328] shadow-xs"
                        : "focus-ring rounded-[5px] border border-transparent px-2 py-0.5 text-[11px] font-medium text-[#59636e] hover:text-[#1f2328]"
                    }
                    onClick={() => setBodyViewMode("preview")}
                    type="button"
                  >
                    미리보기
                  </button>
                  <button
                    aria-pressed={bodyViewMode === "raw"}
                    className={
                      bodyViewMode === "raw"
                        ? "focus-ring rounded-[5px] border border-[#d1d9e0] bg-white px-2 py-0.5 text-[11px] font-semibold text-[#1f2328] shadow-xs"
                        : "focus-ring rounded-[5px] border border-transparent px-2 py-0.5 text-[11px] font-medium text-[#59636e] hover:text-[#1f2328]"
                    }
                    onClick={() => setBodyViewMode("raw")}
                    type="button"
                  >
                    원문
                  </button>
                </div>
              ) : null}
              <button
                className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
                onClick={() => copyToClipboard(displayBody, "body")}
                type="button"
              >
                {copied === "body" ? (
                  <>
                    <Check className="h-3.5 w-3.5 text-[#1a7f37]" />
                    <span>복사됨 ✓</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3.5 w-3.5" />
                    <span>복사</span>
                  </>
                )}
              </button>
            </div>
          </div>
          {editing ? (
            <textarea
              aria-label="PR 본문 편집"
              className="focus-ring min-h-72 w-full resize-y rounded-md border border-[#d1d9e0] bg-white px-3.5 py-2.5 font-mono text-[13px] leading-[1.6] text-[#1f2328] focus:border-[#0969da]"
              onChange={(event) => setEditBody(event.target.value)}
              value={editBody}
            />
          ) : bodyViewMode === "preview" ? (
            <div className="max-h-96 overflow-y-auto rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4">
              <MarkdownPreview content={displayBody} />
            </div>
          ) : (
            <pre className="max-h-96 overflow-y-auto whitespace-pre-wrap rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4 font-mono text-[13px] leading-[1.6] text-[#1f2328]">
              {displayBody}
            </pre>
          )}
        </div>
      </div>
    </section>
  );

  const rightContextPane = (
    <div className="space-y-5">
      {draft.diffContent ? (
        <section className="primer-box overflow-hidden">
          <div className="primer-box-header px-5 py-3">
            <p className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
              코드 변경 내용 (diff)
            </p>
          </div>
          <div className="p-4">
            <DiffViewer diffContent={draft.diffContent} highlightPaths={issueFilePaths} />
          </div>
        </section>
      ) : null}

      <PrDraftReadinessSection readiness={readiness} />
    </div>
  );

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-[#d1d9e0] pb-4">
        <div className="flex flex-wrap items-center gap-2.5 text-xs">
          <Link
            className="focus-ring inline-flex items-center gap-1 font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
            href="/pr-draft"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>PR 초안 목록</span>
          </Link>
          <span className="text-[#d1d9e0]">/</span>
          <span className="font-semibold text-[#1f2328]">연결된 이슈</span>
          <span className="font-mono text-[12px] font-medium tabular-nums text-[#1f2328]">
            {draft.upstreamRepo} #{draft.githubIssueNumber}
          </span>
          <span className="primer-branch-pill">
            {draft.baseBranch} → {draft.headBranch}
          </span>
          <span className="tabular-nums font-mono text-[12px] text-[#59636e]">{formatDate(draft.createdAt)}</span>
          {draft.sourceIssueId ? (
            <Link
              className="focus-ring inline-flex items-center gap-1 font-medium text-[#0969da] hover:underline"
              href={issueDetailHref(draft.sourceIssueId)}
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
            className="focus-ring inline-flex items-center gap-1.5 rounded-md border border-[#ff8182]/40 bg-[#ffebe9] px-3 py-1.5 text-xs font-medium text-[#d1242f] transition-colors hover:bg-[#ffd8d5] disabled:cursor-not-allowed disabled:opacity-50"
            disabled={deleting}
            onClick={openDeleteDialog}
            type="button"
          >
            <Trash2 className="h-3.5 w-3.5" />
            <span className="button-text">{deleting ? "삭제 중..." : "삭제"}</span>
          </button>
        </div>
      </header>

      {error ? (
        <div className="mb-4 rounded-lg border border-[#ff8182]/40 bg-[#ffebe9] px-4 py-3 text-xs font-medium text-[#d1242f]">
          {error}
        </div>
      ) : null}

      {translated ? (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-[#0969da]/25 bg-[#ddf4ff] px-4 py-2.5 text-xs font-medium text-[#1f2328]">
          <span>
            영어 번역이 완료되었습니다. 아래 내용으로 GitHub PR을 작성하세요.
            {safeTranslatedCompare?.bodyTruncated ? " (URL 길이 제한으로 본문 일부 생략)" : ""}
          </span>
          <ExternalBrowserLink
            className="font-semibold text-[#0969da] underline hover:text-[#0550ae]"
            href={safeTranslatedCompare?.url ?? translated.githubUrl}
            rel="noreferrer"
          >
            GitHub에서 PR 올리기 ↗
          </ExternalBrowserLink>
        </div>
      ) : null}

      <StudioSplitPane
        defaultRatio={46}
        leftLabel="PR 초안 편집"
        leftPane={leftDraftPane}
        rightPane={rightContextPane}
        storageKey="pr-detail"
      />

      <dialog
        aria-describedby={deleteDescId}
        aria-labelledby={deleteTitleId}
        className="studio-command-dialog"
        ref={deleteDialogRef}
        {...({ closedby: "any" } as Record<string, unknown>)}
      >
        <div className="overflow-hidden rounded-xl border border-[#d1d9e0] bg-white shadow-xl">
          <div className="primer-box-header flex items-center justify-between px-4 py-3">
            <div className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-[#d1242f]" />
              <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]" id={deleteTitleId}>
                PR 초안 삭제
              </h2>
            </div>
            <button
              aria-label="삭제 대화상자 닫기"
              className="focus-ring inline-flex h-6 w-6 items-center justify-center rounded-md text-[#59636e] hover:bg-[#eaeef2] hover:text-[#1f2328]"
              onClick={closeDeleteDialog}
              type="button"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
          <div className="px-5 py-4">
            <p className="text-[13.5px] leading-[1.55] text-[#59636e]" id={deleteDescId}>
              이 PR 초안을 삭제하시겠습니까? 삭제한 초안과 번역 이력은 되돌릴 수 없습니다.
            </p>
          </div>
          <div className="flex items-center justify-end gap-2 border-t border-[#d1d9e0] bg-[#f6f8fa] px-5 py-3">
            <button
              autoFocus
              className="btn-secondary focus-ring px-3.5 py-1.5 text-xs"
              onClick={closeDeleteDialog}
              ref={cancelDeleteBtnRef}
              type="button"
            >
              취소
            </button>
            <button
              className="focus-ring inline-flex items-center gap-1.5 rounded-md border border-[#1f2328]/15 bg-[#cf222e] px-3.5 py-1.5 text-xs font-medium text-white transition-colors hover:bg-[#a40e26] disabled:opacity-50"
              disabled={deleting}
              onClick={confirmDelete}
              type="button"
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>{deleting ? "삭제 중..." : "삭제 확인"}</span>
            </button>
          </div>
        </div>
      </dialog>
    </ProtectedLayout>
  );
}
