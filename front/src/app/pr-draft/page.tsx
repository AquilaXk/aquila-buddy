"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useId, useMemo, useState } from "react";
import { ArrowLeft, Check, Copy, ExternalLink, GitPullRequest, Globe, Loader2 } from "@/components/icons";
import ProtectedLayout from "@/components/ProtectedLayout";
import { LanguageTabSwitcher } from "@/components/LanguageTabSwitcher";
import { PrDraftReadinessSection } from "@/components/PrDraftReadinessSection";
import { StudioSplitPane } from "@/components/StudioSplitPane";
import { Badge } from "@/components/ui/badge";
import {
  createPullRequestDraft,
  getIssueDetail,
  getPullRequestDraftHistory,
  getPullRequestDraftReadiness,
  getRecommendedIssueHistory,
  translatePullRequestDraft,
  type PullRequestDraftHistoryPage,
  type PullRequestDraftReadiness,
  type PullRequestDraftTranslateResult,
  type RecommendedIssue,
  type RecommendedIssueHistory,
  type StoredPullRequestDraft
} from "@/lib/api";
import { formatDate } from "@/lib/format";
import { buildSafeGitHubCompareUrl } from "@/lib/githubQuickPullUrl";
import { issueDetailHref, prDetailHref } from "@/lib/routes";

type DraftInput = {
  upstreamRepo: string;
  githubIssueNumber: string;
  issueId: string;
  issueTitle: string;
  baseBranch: string;
  headBranch: string;
};

type SearchParamsLike = {
  get(name: string): string | null;
};

const historyPageSize = 5;

export default function PrDraftPage() {
  return (
    <Suspense fallback={<PrDraftFallback />}>
      <PrDraftContent />
    </Suspense>
  );
}

function PrDraftContent() {
  const searchParams = useSearchParams();
  const langIdPrefix = useId();
  const initialDraftInput = useMemo(() => readDraftInputFromSearchParams(searchParams), [searchParams]);
  const [draftInput, setDraftInput] = useState(initialDraftInput);
  const [baseBranch, setBaseBranch] = useState(initialDraftInput.baseBranch);
  const [headBranch, setHeadBranch] = useState(initialDraftInput.headBranch);
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [translated, setTranslated] = useState<PullRequestDraftTranslateResult | null>(null);
  const [readiness, setReadiness] = useState<PullRequestDraftReadiness | null>(null);
  const [activeLangTab, setActiveLangTab] = useState<"ko" | "en">("ko");
  const [error, setError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [sourceIssueLoading, setSourceIssueLoading] = useState(false);
  const [sourceIssueError, setSourceIssueError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [translating, setTranslating] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyPageIndex, setHistoryPageIndex] = useState(0);
  const [historyPage, setHistoryPage] = useState<PullRequestDraftHistoryPage | null>(null);
  const [availableIssues, setAvailableIssues] = useState<RecommendedIssueHistory[]>([]);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);
  const startedWithoutIssueQuery = !initialDraftInput.upstreamRepo || Number(initialDraftInput.githubIssueNumber) <= 0;
  const hasIssueParam = Boolean(draftInput.upstreamRepo.trim() && Number(draftInput.githubIssueNumber) > 0);

  const loadDraftHistory = useCallback(async (page: number) => {
    setHistoryLoading(true);

    try {
      const loadedHistory = await getPullRequestDraftHistory(page, historyPageSize);
      setHistoryPage(loadedHistory);
      setHistoryPageIndex(loadedHistory.page);
      setHistoryError(null);
    } catch (caught) {
      setHistoryError(caught instanceof Error ? caught.message : "PR 초안 이력을 불러오지 못했습니다.");
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDraftHistory(historyPageIndex);
  }, [historyPageIndex, loadDraftHistory]);

  useEffect(() => {
    if (!startedWithoutIssueQuery) {
      return;
    }
    let ignored = false;

    getRecommendedIssueHistory()
      .then((items) => {
        if (!ignored) {
          setAvailableIssues(items);
        }
      })
      .catch(() => {
        // 독립 진입 선택 목록 로드 실패 시 직접 입력 폼은 계속 사용한다.
      });

    return () => {
      ignored = true;
    };
  }, [startedWithoutIssueQuery]);

  useEffect(() => {
    let ignored = false;

    setDraftInput(initialDraftInput);
    setBaseBranch(initialDraftInput.baseBranch);
    setHeadBranch(initialDraftInput.headBranch);
    setSourceIssueError(null);

    if (!initialDraftInput.issueId) {
      setSourceIssueLoading(false);
      return () => {
        ignored = true;
      };
    }

    setSourceIssueLoading(true);

    getIssueDetail(initialDraftInput.issueId)
      .then((issue: RecommendedIssue) => {
        if (ignored) {
          return;
        }

        setDraftInput((currentInput) => mergeDraftInputWithIssue(currentInput, issue));
      })
      .catch((caught) => {
        if (ignored) {
          return;
        }

        setSourceIssueError(
          caught instanceof Error ? caught.message : "이슈 정보를 불러오지 못해 전달된 값으로 계속합니다."
        );
      })
      .finally(() => {
        if (!ignored) {
          setSourceIssueLoading(false);
        }
      });

    return () => {
      ignored = true;
    };
  }, [initialDraftInput]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!hasIssueParam || !baseBranch.trim() || !headBranch.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);
    setDraft(null);
    setTranslated(null);

    try {
      const createdDraft = await createPullRequestDraft({
        upstreamRepo: draftInput.upstreamRepo,
        githubIssueNumber: Number(draftInput.githubIssueNumber),
        sourceIssueId: draftInput.issueId || undefined,
        baseBranch: baseBranch.trim(),
        headBranch: headBranch.trim()
      });

      setDraft(createdDraft);
      setActiveLangTab("ko");
      getPullRequestDraftReadiness(createdDraft.id)
        .then(setReadiness)
        .catch(() => setReadiness(null));

      if (historyPageIndex === 0) {
        await loadDraftHistory(0);
      } else {
        setHistoryPageIndex(0);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "PR 초안 생성에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleTranslate() {
    if (!draft) {
      return;
    }

    setTranslating(true);
    setError(null);

    try {
      const translatedResult = await translatePullRequestDraft(draft.id);
      setTranslated(translatedResult);
      setActiveLangTab("en");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "번역에 실패했습니다.");
    } finally {
      setTranslating(false);
    }
  }

  async function copyDraft(text: string, type: "title" | "body") {
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      }
      setCopied(type);
      window.setTimeout(() => setCopied(null), 1600);
    } catch {
      // clipboard access fallback
    }
  }

  const canSubmit = hasIssueParam && baseBranch.trim() && headBranch.trim();
  const isEnglishTab = activeLangTab === "en";
  const displayTitle = isEnglishTab && translated ? translated.titleEn : (draft?.title ?? "");
  const displayBody = isEnglishTab && translated ? translated.bodyEn : (draft?.body ?? "");
  const drafts = historyPage?.content ?? [];
  const totalHistoryPages = Math.max(historyPage?.totalPages ?? 1, 1);
  const safeCompare = useMemo(() => {
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

  const koTabId = `${langIdPrefix}-tab-ko`;
  const enTabId = `${langIdPrefix}-tab-en`;
  const draftPanelId = `${langIdPrefix}-panel`;

  const leftSetupPane = (
    <aside className="primer-box divide-y divide-[#d1d9e0] overflow-hidden">
      <div className="primer-box-header flex items-center justify-between gap-2 px-4 py-3">
        <span className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">선택된 이슈</span>
        <span className="truncate font-mono text-[12px] text-[#59636e]">
          {hasIssueParam ? `${draftInput.upstreamRepo} #${draftInput.githubIssueNumber}` : "불러오는 중..."}
        </span>
      </div>

      {hasIssueParam || draftInput.issueId ? (
        <div className="p-4">
          <h2 className="text-[15px] font-semibold leading-[1.4] text-[#1f2328]">
            {draftInput.issueTitle || (hasIssueParam ? `Issue #${draftInput.githubIssueNumber}` : draftInput.issueId)}
          </h2>
          {sourceIssueLoading ? (
            <p className="mt-2 text-xs text-[#59636e]">이슈 정보를 불러오는 중...</p>
          ) : null}
          {sourceIssueError ? (
            <p className="mt-2 rounded-md border border-[#9a6700]/25 bg-[#fff8c5] px-3 py-2 text-xs font-medium text-[#9a6700]">
              {sourceIssueError}
            </p>
          ) : null}
        </div>
      ) : (
        <div className="border-b border-[#9a6700]/25 bg-[#fff8c5]/70 p-4">
          <p className="text-[13px] font-medium leading-relaxed text-[#9a6700]">
            이슈 상세 페이지에서 &quot;PR 초안 만들기&quot;를 통해 접근해주세요.{" "}
            <Link className="font-semibold text-[#1f2328] underline" href="/issues">
              이슈 목록으로
            </Link>
          </p>
        </div>
      )}

      {startedWithoutIssueQuery ? (
        <div className="space-y-3 border-b border-[#d1d9e0] p-4">
          {availableIssues.length > 0 ? (
            <label className="block">
              <span className="text-[13px] font-semibold text-[#1f2328]">추천 이슈에서 선택</span>
              <select
                className="focus-ring mt-1.5 w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 text-xs text-[#1f2328] focus:border-[#0969da]"
                onChange={(event) => {
                  const picked = availableIssues.find((item) => item.id === event.target.value);
                  if (!picked) {
                    return;
                  }
                  setDraftInput({
                    upstreamRepo: picked.repoFullName,
                    githubIssueNumber: String(picked.issueNumber),
                    issueId: picked.id,
                    issueTitle: picked.title,
                    baseBranch: baseBranch || "main",
                    headBranch: headBranch || `feat/issue-${picked.issueNumber}`,
                  });
                  if (!headBranch) {
                    setHeadBranch(`feat/issue-${picked.issueNumber}`);
                  }
                }}
                value={draftInput.issueId}
              >
                <option value="">추천 이슈 선택...</option>
                {availableIssues.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.repoFullName} #{item.issueNumber} — {item.title}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
            <label className="block sm:col-span-2">
              <span className="text-[13px] font-semibold text-[#1f2328]">
                대상 저장소 <span className="text-[#d1242f]">*</span>
              </span>
              <input
                className="focus-ring mt-1.5 w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 font-mono text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
                onChange={(event) =>
                  setDraftInput((prev) => ({ ...prev, upstreamRepo: event.target.value }))
                }
                placeholder="owner/repo"
                value={draftInput.upstreamRepo}
              />
            </label>
            <label className="block">
              <span className="text-[13px] font-semibold text-[#1f2328]">
                이슈 번호 <span className="text-[#d1242f]">*</span>
              </span>
              <input
                className="focus-ring mt-1.5 w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 font-mono text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
                min={1}
                onChange={(event) =>
                  setDraftInput((prev) => ({ ...prev, githubIssueNumber: event.target.value }))
                }
                placeholder="42"
                type="number"
                value={draftInput.githubIssueNumber}
              />
            </label>
          </div>
        </div>
      ) : null}

      {hasIssueParam ? (
        <form className="space-y-4 p-4" onSubmit={handleSubmit}>
          <label className="block">
            <span className="text-[13px] font-semibold text-[#1f2328]">
              Base 브랜치 <span className="text-[#d1242f]">*</span>
            </span>
            <input
              className="focus-ring mt-1.5 w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 font-mono text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
              onChange={(event) => setBaseBranch(event.target.value)}
              placeholder="main"
              value={baseBranch}
            />
            <span className="mt-1 block text-xs text-[#59636e]">
              병합 대상 기본 브랜치
            </span>
          </label>

          <label className="block">
            <span className="text-[13px] font-semibold text-[#1f2328]">
              Head 브랜치 <span className="text-[#d1242f]">*</span>
            </span>
            <input
              className="focus-ring mt-1.5 w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 font-mono text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
              onChange={(event) => setHeadBranch(event.target.value)}
              placeholder="feat/my-feature 또는 fork-owner:feat/my-feature"
              value={headBranch}
            />
            <span className="mt-1 block text-xs text-[#59636e]">
              작업 브랜치명 (포크 저장소는 fork-owner:branch 형식)
            </span>
          </label>

          {!draft && error ? (
            <div className="rounded-md border border-[#ff8182]/40 bg-[#ffebe9] px-3 py-2 text-xs font-medium text-[#d1242f]">
              {error}
            </div>
          ) : null}

          <button
            className="btn-primary focus-ring inline-flex w-full items-center justify-center gap-2 px-4 py-2 text-[13px] disabled:cursor-not-allowed disabled:opacity-50"
            disabled={submitting || !canSubmit}
            type="submit"
          >
            {submitting ? (
              <>
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                <span className="button-text">AI가 PR 초안 생성 중...</span>
              </>
            ) : (
              <>
                <GitPullRequest className="h-3.5 w-3.5" />
                <span className="button-text">PR 초안 생성</span>
              </>
            )}
          </button>
        </form>
      ) : null}
    </aside>
  );

  const rightDraftPane = (
    <div className="space-y-5">
      <section className="primer-box divide-y divide-[#d1d9e0] overflow-hidden">
        <div className="primer-box-header flex flex-wrap items-center justify-between gap-3 px-5 py-3">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
              생성된 PR 초안
            </h2>
            {baseBranch && headBranch ? (
              <span className="primer-branch-pill">
                {baseBranch} ← {headBranch}
              </span>
            ) : null}
          </div>

          {draft ? (
            <div className="flex flex-wrap items-center gap-2">
              <LanguageTabSwitcher
                activeTab={activeLangTab}
                hasTranslation={Boolean(translated)}
                onSelectTab={setActiveLangTab}
                panelIds={{ ko: draftPanelId, en: draftPanelId }}
                tabIds={{ ko: koTabId, en: enTabId }}
              />
              {!translated ? (
                <button
                  className="btn-secondary focus-ring inline-flex items-center gap-1.5 px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={translating}
                  onClick={handleTranslate}
                  type="button"
                >
                  {translating ? (
                    <>
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      <span className="button-text">영어로 번역 중...</span>
                    </>
                  ) : (
                    <>
                      <Globe className="h-3.5 w-3.5" />
                      <span className="button-text">영어로 번역하기</span>
                    </>
                  )}
                </button>
              ) : (
                <a
                  className="btn-primer-green focus-ring inline-flex items-center gap-1.5 px-3 py-1.5 text-xs"
                  href={safeCompare?.url ?? translated.githubUrl}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <ExternalLink className="h-3.5 w-3.5" />
                  <span className="button-text">GitHub에서 PR 올리기 ↗</span>
                </a>
              )}
              <Link
                className="btn-secondary focus-ring px-2.5 py-1.5 text-xs font-medium text-[#0969da]"
                href={prDetailHref(draft.id)}
              >
                상세 보기 (수정·삭제) →
              </Link>
            </div>
          ) : null}
        </div>

        {safeCompare?.bodyTruncated ? (
          <div className="border-b border-[#9a6700]/25 bg-[#fff8c5] px-5 py-2.5 text-xs text-[#9a6700]">
            URL 길이 제한으로 본문 일부가 생략되었습니다. GitHub 화면에서 아래 복사 버튼으로 전체 본문을 붙여넣으세요.
          </div>
        ) : null}

        {draft && error ? (
          <div className="bg-[#ffebe9] px-5 py-3 text-xs font-medium text-[#d1242f]">
            <p className="font-semibold">PR 초안 작업 오류</p>
            <p className="mt-0.5">{error}</p>
          </div>
        ) : null}

        {draft ? (
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
                  onClick={() => copyDraft(displayTitle, "title")}
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
              <p className="text-[15px] font-semibold leading-[1.45] text-[#1f2328]">{displayTitle}</p>
            </div>

            <div className="px-5 py-4">
              <div className="mb-2 flex items-center justify-between gap-3">
                <span className="text-xs font-semibold text-[#59636e]">PR 본문</span>
                <button
                  className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
                  onClick={() => copyDraft(displayBody, "body")}
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
              <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4 font-mono text-[13px] leading-[1.6] text-[#1f2328]">
                {displayBody}
              </pre>
            </div>
          </div>
        ) : (
          <div className="divide-y divide-[#d1d9e0]">
            <dl className="grid grid-cols-1 divide-y divide-[#d1d9e0] bg-[#f6f8fa] text-xs sm:grid-cols-3 sm:divide-x sm:divide-y-0">
              <div className="px-4 py-3">
                <dt className="text-[#59636e]">대상 저장소</dt>
                <dd className="mt-0.5 truncate font-mono font-medium text-[#1f2328]">
                  {draftInput.upstreamRepo || "미지정"}
                </dd>
              </div>
              <div className="px-4 py-3">
                <dt className="text-[#59636e]">이슈 번호</dt>
                <dd className="tabular-nums mt-0.5 font-mono font-medium text-[#1f2328]">
                  {draftInput.githubIssueNumber ? `#${draftInput.githubIssueNumber}` : "미지정"}
                </dd>
              </div>
              <div className="px-4 py-3">
                <dt className="text-[#59636e]">비교 브랜치</dt>
                <dd className="mt-0.5 truncate font-mono font-medium text-[#1f2328]">
                  {baseBranch || "main"} ← {headBranch || "미입력"}
                </dd>
              </div>
            </dl>
            <div className="px-5 py-12 text-center text-[13.5px] leading-[1.55] text-[#59636e]">
              선택한 이슈의 브랜치 정보를 입력하면 PR 초안 결과가 여기에 표시됩니다.
            </div>
          </div>
        )}
      </section>

      <PrDraftReadinessSection readiness={readiness} />
    </div>
  );

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-[#d1d9e0] pb-5">
        <div>
          <div className="mb-1.5 flex items-center gap-2 text-xs text-[#59636e]">
            {draftInput.issueId ? (
              <Link
                className="focus-ring inline-flex items-center gap-1 font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
                href={issueDetailHref(draftInput.issueId)}
              >
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>이슈로 돌아가기</span>
              </Link>
            ) : (
              <Link
                className="focus-ring inline-flex items-center gap-1 font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
                href="/issues"
              >
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>추천 이슈</span>
              </Link>
            )}
          </div>
          <h1 className="text-[22px] font-semibold leading-[1.3] tracking-[-0.025em] text-[#1f2328]">
            PR 초안 생성
          </h1>
        </div>
      </header>

      <StudioSplitPane
        defaultRatio={35}
        leftLabel="PR 설정"
        leftPane={leftSetupPane}
        rightPane={rightDraftPane}
        storageKey="pr-draft"
      />

      <section className="primer-box studio-deferred-block mt-8 overflow-hidden">
        <div className="primer-box-header flex items-center justify-between gap-3 px-5 py-3">
          <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">PR 초안 이력</h2>
          {historyPage ? (
            <Badge variant="counter">총 {historyPage.totalElements}건</Badge>
          ) : null}
        </div>

        {historyError ? (
          <div className="bg-[#ffebe9] px-5 py-3 text-xs font-medium text-[#d1242f]">
            {historyError}
          </div>
        ) : null}

        {historyLoading ? (
          <div className="divide-y divide-[#d1d9e0]">
            <div className="h-14 animate-pulse bg-white" />
            <div className="h-14 animate-pulse bg-white" />
            <div className="h-14 animate-pulse bg-white" />
          </div>
        ) : drafts.length === 0 ? (
          <div className="py-10 text-center text-[13.5px] text-[#59636e]">
            아직 생성된 PR 초안이 없습니다.
          </div>
        ) : (
          <>
            <div className="divide-y divide-[#d1d9e0]">
              {drafts.map((item) => (
                <Link
                  className="focus-ring group flex items-center justify-between gap-4 border-l-[3px] border-l-transparent px-5 py-3.5 transition-colors hover:border-l-[#0969da] hover:bg-[#f6f8fa]"
                  href={prDetailHref(item.id)}
                  key={item.id}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[15px] font-semibold leading-[1.4] text-[#1f2328] transition-colors group-hover:text-[#0969da]">
                      {item.title}
                    </p>
                    <p className="tabular-nums mt-1 font-mono text-[12px] text-[#59636e]">
                      {item.upstreamRepo} #{item.githubIssueNumber} · {item.baseBranch} → {item.headBranch} · {formatDate(item.createdAt)}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs font-medium text-[#0969da] transition-colors group-hover:underline">
                    상세 보기
                  </span>
                </Link>
              ))}
            </div>

            {totalHistoryPages > 1 ? (
              <div className="flex items-center justify-between border-t border-[#d1d9e0] bg-[#f6f8fa] px-5 py-2.5">
                <button
                  className="btn-secondary focus-ring px-3 py-1 text-xs disabled:opacity-40"
                  disabled={historyLoading || historyPage?.first}
                  onClick={() => setHistoryPageIndex((page) => Math.max(0, page - 1))}
                  type="button"
                >
                  이전
                </button>
                <span className="tabular-nums font-mono text-xs text-[#59636e]">
                  {(historyPage?.page ?? 0) + 1} / {totalHistoryPages}
                </span>
                <button
                  className="btn-secondary focus-ring px-3 py-1 text-xs disabled:opacity-40"
                  disabled={historyLoading || historyPage?.last}
                  onClick={() => setHistoryPageIndex((page) => page + 1)}
                  type="button"
                >
                  다음
                </button>
              </div>
            ) : null}
          </>
        )}
      </section>
    </ProtectedLayout>
  );
}

function PrDraftFallback() {
  return (
    <ProtectedLayout>
      <div className="primer-box p-5 text-[13.5px] text-[#59636e]">불러오는 중...</div>
    </ProtectedLayout>
  );
}

function readDraftInputFromSearchParams(searchParams: SearchParamsLike): DraftInput {
  const upstreamRepo = readTextParam(searchParams, "repo", "");
  const githubIssueNumber = readIssueNumberParam(searchParams);
  const issueTitle = readTextParam(searchParams, "issueTitle", "");

  return {
    upstreamRepo,
    githubIssueNumber,
    issueId: readTextParam(searchParams, "issueId", readTextParam(searchParams, "sourceIssueId", "")),
    issueTitle,
    baseBranch: readTextParam(searchParams, "base", "main"),
    headBranch: readTextParam(searchParams, "head", "")
  };
}

function mergeDraftInputWithIssue(currentInput: DraftInput, issue: RecommendedIssue): DraftInput {
  const upstreamRepo = currentInput.upstreamRepo || issue.repoFullName;
  const githubIssueNumber = currentInput.githubIssueNumber || String(issue.issueNumber);
  const issueTitle = currentInput.issueTitle || issue.title;

  return {
    ...currentInput,
    upstreamRepo,
    githubIssueNumber,
    issueTitle
  };
}

function readTextParam(searchParams: SearchParamsLike, name: string, fallback: string) {
  const value = searchParams.get(name);

  return value && value.trim() ? value : fallback;
}

function readIssueNumberParam(searchParams: SearchParamsLike) {
  const value = searchParams.get("issueNumber") ?? searchParams.get("issue");
  const issueNumber = Number(value);

  if (!Number.isInteger(issueNumber) || issueNumber <= 0) {
    return "";
  }

  return String(issueNumber);
}
