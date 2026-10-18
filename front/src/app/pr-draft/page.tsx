"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, Copy, Check, Globe, ExternalLink, GitPullRequest, Loader2 } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { PrDraftReadinessSection } from "@/components/PrDraftReadinessSection";
import { LanguageTabSwitcher } from "@/components/LanguageTabSwitcher";
import {
  createPullRequestDraft,
  getIssueDetail,
  getPullRequestDraftHistory,
  getPullRequestDraftReadiness,
  translatePullRequestDraft,
  type PullRequestDraftHistoryPage,
  type PullRequestDraftReadiness,
  type PullRequestDraftTranslateResult,
  type RecommendedIssue,
  type StoredPullRequestDraft
} from "@/lib/api";
import { formatDate } from "@/lib/format";

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
  const [copied, setCopied] = useState<"title" | "body" | null>(null);
  const hasIssueParam = Boolean(draftInput.upstreamRepo && Number(draftInput.githubIssueNumber) > 0);

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

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-3 border-b border-neutral-border pb-5">
        <div>
          <div className="mb-1.5 flex items-center gap-2 text-xs text-ink-secondary">
            {draftInput.issueId ? (
              <Link
                className="focus-ring inline-flex items-center gap-1 font-medium text-ink-secondary transition-colors hover:text-ink-primary"
                href={`/issues/${encodeURIComponent(draftInput.issueId)}`}
              >
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>이슈로 돌아가기</span>
              </Link>
            ) : (
              <Link
                className="focus-ring inline-flex items-center gap-1 font-medium text-ink-secondary transition-colors hover:text-ink-primary"
                href="/issues"
              >
                <ArrowLeft className="h-3.5 w-3.5" />
                <span>추천 이슈</span>
              </Link>
            )}
          </div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] text-ink-primary">PR 초안 생성</h1>
        </div>
      </header>

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <aside className="surface divide-y divide-neutral-border overflow-hidden lg:col-span-4">
          {hasIssueParam || draftInput.issueId ? (
            <div className="p-4">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-semibold text-ink-primary">선택된 이슈</span>
                <span className="truncate font-mono text-xs text-ink-muted">
                  {hasIssueParam ? `${draftInput.upstreamRepo} #${draftInput.githubIssueNumber}` : "불러오는 중..."}
                </span>
              </div>
              <h2 className="mt-1.5 text-sm font-medium leading-snug text-ink-primary">
                {draftInput.issueTitle || (hasIssueParam ? `Issue #${draftInput.githubIssueNumber}` : draftInput.issueId)}
              </h2>
              {sourceIssueLoading ? (
                <p className="mt-2 text-xs text-ink-muted">이슈 정보를 불러오는 중...</p>
              ) : null}
              {sourceIssueError ? (
                <p className="mt-2 text-xs font-medium text-[#b06000]">{sourceIssueError}</p>
              ) : null}
            </div>
          ) : (
            <div className="bg-[#fef7e0] p-4">
              <p className="text-xs font-medium leading-relaxed text-[#b06000]">
                이슈 상세 페이지에서 &quot;PR 초안 만들기&quot;를 통해 접근해주세요.{" "}
                <Link className="underline hover:text-ink-primary" href="/issues">
                  이슈 목록으로
                </Link>
              </p>
            </div>
          )}

          {hasIssueParam ? (
            <form className="space-y-3.5 p-4" onSubmit={handleSubmit}>
              <label className="block">
                <span className="text-xs font-medium text-ink-secondary">
                  Base 브랜치 <span className="text-[#c5221f]">*</span>
                </span>
                <input
                  className="focus-ring mt-1 w-full rounded border border-neutral-border bg-white px-3 py-1.5 font-mono text-xs text-ink-primary placeholder:text-ink-muted focus:border-brand-500"
                  onChange={(event) => setBaseBranch(event.target.value)}
                  placeholder="main"
                  value={baseBranch}
                />
                <span className="mt-1 block text-[11px] text-ink-muted">
                  병합 대상 기본 브랜치
                </span>
              </label>

              <label className="block">
                <span className="text-xs font-medium text-ink-secondary">
                  Head 브랜치 <span className="text-[#c5221f]">*</span>
                </span>
                <input
                  className="focus-ring mt-1 w-full rounded border border-neutral-border bg-white px-3 py-1.5 font-mono text-xs text-ink-primary placeholder:text-ink-muted focus:border-brand-500"
                  onChange={(event) => setHeadBranch(event.target.value)}
                  placeholder="feat/my-feature"
                  value={headBranch}
                />
                <span className="mt-1 block text-[11px] text-ink-muted">
                  변경 사항이 푸시된 작업 브랜치
                </span>
              </label>

              {!draft && error ? (
                <div className="rounded border border-[#c5221f]/20 bg-[#fce8e6] px-3 py-2 text-xs font-medium text-[#c5221f]">
                  {error}
                </div>
              ) : null}

              <button
                className="btn-primary focus-ring inline-flex w-full items-center justify-center gap-2 px-4 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-50"
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

        <div className="space-y-5 lg:col-span-8">
          <section className="surface divide-y divide-neutral-border overflow-hidden">
            <div className="sheet-header flex flex-wrap items-center justify-between gap-3 px-5 py-3">
              <div className="flex items-center gap-2">
                <h2 className="text-xs font-semibold text-ink-primary">생성된 PR 초안</h2>
                {baseBranch && headBranch ? (
                  <span className="rounded border border-neutral-border bg-white px-2 py-0.5 font-mono text-[11px] text-ink-secondary">
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
                  />
                  {!translated ? (
                    <button
                      className="btn-secondary focus-ring inline-flex items-center gap-1.5 px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-50"
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
                      className="btn-primary focus-ring inline-flex items-center gap-1.5 px-3 py-1 text-xs"
                      href={translated.githubUrl}
                      rel="noopener noreferrer"
                      target="_blank"
                    >
                      <ExternalLink className="h-3.5 w-3.5" />
                      <span className="button-text">GitHub에서 PR 올리기 ↗</span>
                    </a>
                  )}
                  <Link
                    className="focus-ring text-xs font-medium text-brand-500 transition-colors hover:text-brand-600"
                    href={`/pr/${draft.id}`}
                  >
                    상세 보기 (수정·삭제) →
                  </Link>
                </div>
              ) : null}
            </div>

            {draft && error ? (
              <div className="bg-[#fce8e6] px-5 py-3 text-xs font-medium text-[#c5221f]">
                <p className="font-semibold">PR 초안 작업 오류</p>
                <p className="mt-0.5">{error}</p>
              </div>
            ) : null}

            {draft ? (
              <>
                <div className="px-5 py-4">
                  <div className="mb-1.5 flex items-center justify-between gap-3">
                    <span className="text-xs font-semibold text-ink-secondary">PR 제목</span>
                    <button
                      className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-ink-secondary transition-colors hover:text-brand-500"
                      onClick={() => copyDraft(displayTitle, "title")}
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
                  <p className="text-sm font-medium leading-6 text-ink-primary">{displayTitle}</p>
                </div>

                <div className="px-5 py-4">
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <span className="text-xs font-semibold text-ink-secondary">PR 본문</span>
                    <button
                      className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-ink-secondary transition-colors hover:text-brand-500"
                      onClick={() => copyDraft(displayBody, "body")}
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
                  <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded border border-neutral-border bg-neutral-subtle p-4 font-mono text-xs leading-6 text-ink-primary">
                    {displayBody}
                  </pre>
                </div>
              </>
            ) : (
              <div className="divide-y divide-neutral-border">
                <dl className="grid grid-cols-1 divide-y divide-neutral-border text-xs sm:grid-cols-3 sm:divide-x sm:divide-y-0">
                  <div className="px-4 py-3">
                    <dt className="text-ink-muted">대상 저장소</dt>
                    <dd className="mt-0.5 truncate font-mono font-medium text-ink-primary">
                      {draftInput.upstreamRepo || "미지정"}
                    </dd>
                  </div>
                  <div className="px-4 py-3">
                    <dt className="text-ink-muted">이슈 번호</dt>
                    <dd className="tabular-nums mt-0.5 font-mono font-medium text-ink-primary">
                      {draftInput.githubIssueNumber ? `#${draftInput.githubIssueNumber}` : "미지정"}
                    </dd>
                  </div>
                  <div className="px-4 py-3">
                    <dt className="text-ink-muted">비교 브랜치</dt>
                    <dd className="mt-0.5 truncate font-mono font-medium text-ink-primary">
                      {baseBranch || "main"} ← {headBranch || "미입력"}
                    </dd>
                  </div>
                </dl>
                <div className="px-5 py-10 text-center text-xs leading-relaxed text-ink-secondary">
                  선택한 이슈의 브랜치 정보를 입력하면 PR 초안 결과가 여기에 표시됩니다.
                </div>
              </div>
            )}
          </section>

          <PrDraftReadinessSection readiness={readiness} />
        </div>
      </div>

      <section className="surface mt-8 overflow-hidden">
        <div className="sheet-header flex items-center justify-between gap-3 px-5 py-3">
          <h2 className="text-xs font-semibold text-ink-primary">PR 초안 이력</h2>
          {historyPage ? (
            <span className="tabular-nums font-mono text-xs text-ink-muted">
              총 {historyPage.totalElements}건
            </span>
          ) : null}
        </div>

        {historyError ? (
          <div className="bg-[#fce8e6] px-5 py-3 text-xs font-medium text-[#c5221f]">
            {historyError}
          </div>
        ) : null}

        {historyLoading ? (
          <div className="divide-y divide-neutral-border">
            <div className="h-14 animate-pulse bg-white" />
            <div className="h-14 animate-pulse bg-white" />
            <div className="h-14 animate-pulse bg-white" />
          </div>
        ) : drafts.length === 0 ? (
          <div className="py-10 text-center text-xs text-ink-secondary">
            아직 생성된 PR 초안이 없습니다.
          </div>
        ) : (
          <>
            <div className="divide-y divide-neutral-border">
              {drafts.map((item) => (
                <Link
                  className="focus-ring group flex items-center justify-between gap-4 px-5 py-3 transition-colors hover:bg-neutral-subtle"
                  href={`/pr/${item.id}`}
                  key={item.id}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-ink-primary transition-colors group-hover:text-brand-500">
                      {item.title}
                    </p>
                    <p className="tabular-nums mt-0.5 font-mono text-xs text-ink-muted">
                      {item.upstreamRepo} #{item.githubIssueNumber} · {item.baseBranch} → {item.headBranch} · {formatDate(item.createdAt)}
                    </p>
                  </div>
                  <span className="shrink-0 text-xs font-medium text-brand-500 transition-colors group-hover:text-brand-600">
                    상세 보기
                  </span>
                </Link>
              ))}
            </div>

            {totalHistoryPages > 1 ? (
              <div className="flex items-center justify-between border-t border-neutral-border bg-neutral-subtle px-5 py-2.5">
                <button
                  className="btn-secondary focus-ring px-2.5 py-1 text-xs disabled:opacity-30"
                  disabled={historyLoading || historyPage?.first}
                  onClick={() => setHistoryPageIndex((page) => Math.max(0, page - 1))}
                  type="button"
                >
                  이전
                </button>
                <span className="tabular-nums font-mono text-xs text-ink-secondary">
                  {(historyPage?.page ?? 0) + 1} / {totalHistoryPages}
                </span>
                <button
                  className="btn-secondary focus-ring px-2.5 py-1 text-xs disabled:opacity-30"
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
      <div className="surface p-5 text-xs text-ink-secondary">불러오는 중...</div>
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
