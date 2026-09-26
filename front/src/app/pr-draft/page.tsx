"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ArrowLeft, Copy, Check, Globe, ExternalLink, GitPullRequest, Loader2, CheckCircle2, AlertCircle } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
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
  const [readinessFilter, setReadinessFilter] = useState<"all" | "action-required">("all");
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
      <div className="mb-6 flex items-center gap-3">
        {draftInput.issueId ? (
          <Link
            className="focus-ring inline-flex items-center gap-1.5 text-sm font-semibold text-dark-muted hover:text-dark-text transition-colors"
            href={`/issues/${encodeURIComponent(draftInput.issueId)}`}
          >
            <ArrowLeft className="h-4 w-4" />
            <span>← 이슈로 돌아가기</span>
          </Link>
        ) : (
          <Link
            className="focus-ring inline-flex items-center gap-1.5 text-sm font-semibold text-dark-muted hover:text-dark-text transition-colors"
            href="/issues"
          >
            <ArrowLeft className="h-4 w-4" />
            <span>← 추천 이슈</span>
          </Link>
        )}
        <h1 className="text-2xl font-bold text-dark-text">PR 초안 생성</h1>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          {hasIssueParam || draftInput.issueId ? (
            <section className="surface rounded-xl p-5">
              <p className="text-xs font-semibold uppercase tracking-wider text-brand-400">선택된 이슈</p>
              <p className="mt-2 break-all font-mono text-xs font-semibold text-dark-muted">
                {hasIssueParam ? `${draftInput.upstreamRepo} #${draftInput.githubIssueNumber}` : "이슈 정보를 불러오는 중..."}
              </p>
              <h2 className="mt-2 text-base font-semibold leading-6 text-dark-text">
                {draftInput.issueTitle || (hasIssueParam ? `Issue #${draftInput.githubIssueNumber}` : draftInput.issueId)}
              </h2>
              {sourceIssueLoading ? (
                <p className="mt-3 text-xs font-semibold text-dark-muted">이슈 정보를 불러오는 중...</p>
              ) : null}
              {sourceIssueError ? (
                <p className="mt-3 text-xs font-semibold text-amber-300">{sourceIssueError}</p>
              ) : null}
            </section>
          ) : (
            <section className="surface rounded-xl border-amber-500/30 bg-amber-500/10 p-5">
              <p className="text-sm font-semibold text-amber-300">
                이슈 상세 페이지에서 "PR 초안 만들기"를 통해 접근해주세요.{" "}
                <Link className="underline hover:text-amber-200" href="/issues">
                  이슈 목록으로
                </Link>
              </p>
            </section>
          )}

          {hasIssueParam ? (
            <form className="space-y-4" onSubmit={handleSubmit}>
              <label className="block">
                <span className="text-xs font-semibold uppercase tracking-wide text-dark-muted">
                  Base 브랜치 <span className="text-red-400">*</span>
                </span>
                <input
                  className="focus-ring mt-2 w-full rounded-xl border border-dark-border bg-dark-scaffold px-3.5 py-2.5 font-mono text-sm text-dark-text placeholder:text-dark-muted focus:border-brand-500"
                  onChange={(event) => setBaseBranch(event.target.value)}
                  placeholder="main"
                  value={baseBranch}
                />
                <span className="mt-1 block text-xs text-dark-muted">
                  PR의 대상 브랜치입니다.
                </span>
              </label>

              <label className="block">
                <span className="text-xs font-semibold uppercase tracking-wide text-dark-muted">
                  Head 브랜치 <span className="text-red-400">*</span>
                </span>
                <input
                  className="focus-ring mt-2 w-full rounded-xl border border-dark-border bg-dark-scaffold px-3.5 py-2.5 font-mono text-sm text-dark-text placeholder:text-dark-muted focus:border-brand-500"
                  onChange={(event) => setHeadBranch(event.target.value)}
                  placeholder="feat/my-feature"
                  value={headBranch}
                />
                <span className="mt-1 block text-xs text-dark-muted">
                  내 작업 브랜치입니다.
                </span>
              </label>

              {!draft && error ? (
                <div className="rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm font-semibold text-red-400">
                  {error}
                </div>
              ) : null}

              <button
                className="focus-ring flex w-full items-center justify-center gap-2 rounded-xl bg-brand-500 px-4 py-3 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-600 active:bg-brand-700 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={submitting || !canSubmit}
                type="submit"
              >
                {submitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>AI가 PR 초안 생성 중...</span>
                  </>
                ) : (
                  <>
                    <GitPullRequest className="h-4 w-4" />
                    <span>PR 초안 생성</span>
                  </>
                )}
              </button>
            </form>
          ) : null}
        </div>

        <section className="surface min-h-80 rounded-xl p-5">
          {draft && error ? (
            <div className="mb-4 rounded-xl border border-red-500/30 bg-red-500/10 px-4 py-3 text-sm font-semibold text-red-400">
              <p className="text-xs uppercase tracking-wide text-red-300">PR 초안 작업 오류</p>
              <p className="mt-1">{error}</p>
            </div>
          ) : null}

          {draft ? (
            <div className="space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-dark-border pb-3">
                <h2 className="text-base font-semibold text-dark-text">생성된 PR 초안</h2>
                <div className="flex items-center gap-1 rounded-xl border border-dark-border bg-dark-scaffold p-1">
                  <button
                    className={`focus-ring rounded-lg px-3 py-1 text-xs font-semibold transition-all ${
                      activeLangTab === "ko"
                        ? "bg-brand-500 text-white shadow-sm"
                        : "text-dark-muted hover:text-dark-text"
                    }`}
                    onClick={() => setActiveLangTab("ko")}
                    type="button"
                  >
                    한국어 원본
                  </button>
                  <button
                    className={`focus-ring rounded-lg px-3 py-1 text-xs font-semibold transition-all ${
                      activeLangTab === "en"
                        ? "bg-brand-500 text-white shadow-sm"
                        : "text-dark-muted hover:text-dark-text"
                    }`}
                    onClick={() => setActiveLangTab("en")}
                    type="button"
                  >
                    English {translated ? "✓" : ""}
                  </button>
                </div>
              </div>

              <div className="rounded-xl border border-dark-border bg-dark-scaffold/70 p-4">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-dark-muted">PR 제목</p>
                  <button
                    className="focus-ring inline-flex items-center gap-1 text-xs font-semibold text-dark-muted hover:text-brand-400 transition-colors"
                    onClick={() => copyDraft(displayTitle, "title")}
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
                <p className="text-sm font-semibold leading-6 text-dark-text">{displayTitle}</p>
              </div>

              <div className="rounded-xl border border-dark-border bg-dark-scaffold/70 p-4">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold uppercase tracking-wide text-dark-muted">PR 본문</p>
                  <button
                    className="focus-ring inline-flex items-center gap-1 text-xs font-semibold text-dark-muted hover:text-brand-400 transition-colors"
                    onClick={() => copyDraft(displayBody, "body")}
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
                <pre className="max-h-64 overflow-auto whitespace-pre-wrap font-mono text-xs leading-6 text-dark-text">
                  {displayBody}
                </pre>
              </div>

              {readiness ? (
                <div className="rounded-xl border border-dark-border bg-dark-scaffold/70 p-4 space-y-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <h3 className="text-xs font-semibold uppercase tracking-wider text-brand-400">
                        7대 기여 준비도
                      </h3>
                      <span className="rounded-md border border-dark-border bg-dark-surface px-2 py-0.5 font-mono text-xs font-semibold text-dark-text">
                        {readiness.readyCount} / {readiness.items.length} 준비 완료
                      </span>
                    </div>
                    <div className="flex items-center gap-1 text-xs">
                      <button
                        className={`focus-ring rounded-lg px-2.5 py-1 font-semibold transition-colors ${
                          readinessFilter === "all"
                            ? "border border-dark-border bg-dark-elevated text-dark-text"
                            : "text-dark-muted hover:text-dark-text"
                        }`}
                        onClick={() => setReadinessFilter("all")}
                        type="button"
                      >
                        전체 ({readiness.items.length})
                      </button>
                      <button
                        className={`focus-ring rounded-lg px-2.5 py-1 font-semibold transition-colors ${
                          readinessFilter === "action-required"
                            ? "border border-dark-border bg-dark-elevated text-dark-text"
                            : "text-dark-muted hover:text-dark-text"
                        }`}
                        onClick={() => setReadinessFilter("action-required")}
                        type="button"
                      >
                        확인 필요 ({readiness.items.filter((item) => item.status === "ACTION_REQUIRED").length})
                      </button>
                    </div>
                  </div>

                  <div className="h-1.5 w-full overflow-hidden rounded-full bg-dark-surface">
                    <div
                      className="h-full rounded-full bg-brand-500 transition-all duration-300"
                      style={{
                        width: `${(readiness.readyCount / Math.max(readiness.items.length, 1)) * 100}%`
                      }}
                    />
                  </div>

                  <div className="divide-y divide-dark-border/60">
                    {readiness.items.filter((item) => readinessFilter === "all" || item.status === "ACTION_REQUIRED").length === 0 ? (
                      <p className="py-3 text-center text-xs text-dark-muted">
                        확인 필요한 항목이 없습니다. 모든 준비도 게이트를 통과했습니다.
                      </p>
                    ) : null}
                    {readiness.items
                      .filter((item) => readinessFilter === "all" || item.status === "ACTION_REQUIRED")
                      .map((item) => (
                        <div className="flex items-start gap-2.5 py-2.5 first:pt-1 last:pb-1" key={item.key}>
                          {item.status === "READY" ? (
                            <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                          ) : (
                            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-amber-400" />
                          )}
                          <div className="min-w-0 flex-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-xs font-semibold text-dark-text">{item.label}</span>
                              <span
                                className={`rounded border px-1.5 py-0.5 text-[10px] font-semibold ${
                                  item.status === "READY"
                                    ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
                                    : "border-amber-500/30 bg-amber-500/10 text-amber-400"
                                }`}
                              >
                                {item.status === "READY" ? "준비됨" : "확인 필요"}
                              </span>
                            </div>
                            <p className="mt-0.5 text-[11px] leading-relaxed text-dark-muted">{item.description}</p>
                            <p className="mt-1 break-words rounded border border-dark-border/40 bg-dark-surface/60 px-2 py-1 font-mono text-[11px] text-dark-text/80">
                              {item.detail}
                            </p>
                          </div>
                        </div>
                      ))}
                  </div>
                </div>
              ) : null}

              {!translated ? (
                <button
                  className="focus-ring flex w-full items-center justify-center gap-2 rounded-xl border border-brand-500/40 bg-brand-500/10 px-4 py-2.5 text-sm font-semibold text-brand-400 hover:bg-brand-500/20 transition-colors disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={translating}
                  onClick={handleTranslate}
                  type="button"
                >
                  {translating ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      <span>영어로 번역 중...</span>
                    </>
                  ) : (
                    <>
                      <Globe className="h-4 w-4" />
                      <span>영어로 번역하기</span>
                    </>
                  )}
                </button>
              ) : (
                <a
                  className="focus-ring flex w-full items-center justify-center gap-2 rounded-xl bg-brand-500 px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 active:bg-brand-700 transition-all"
                  href={translated.githubUrl}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  <ExternalLink className="h-4 w-4" />
                  <span>GitHub에서 PR 올리기 ↗</span>
                </a>
              )}

              <Link
                className="focus-ring block text-center text-xs font-semibold text-dark-muted hover:text-brand-400 transition-colors"
                href={`/pr/${draft.id}`}
              >
                상세 보기 (수정·삭제) →
              </Link>
            </div>
          ) : (
            <div className="flex h-full min-h-72 items-center justify-center text-center text-sm leading-6 text-dark-muted">
              선택한 이슈의 브랜치 정보를 입력하면 PR 초안 결과가 여기에 표시됩니다.
            </div>
          )}
        </section>
      </div>

      <section className="mt-10">
        <h2 className="mb-4 font-semibold text-dark-text">PR 초안 이력</h2>
        {historyError ? (
          <div className="surface mb-4 rounded-xl border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
            {historyError}
          </div>
        ) : null}
        {historyLoading ? (
          <div className="space-y-2.5">
            <div className="surface h-16 rounded-xl animate-pulse" />
            <div className="surface h-16 rounded-xl animate-pulse" />
            <div className="surface h-16 rounded-xl animate-pulse" />
          </div>
        ) : drafts.length === 0 ? (
          <div className="surface rounded-xl py-12 text-center text-sm font-semibold text-dark-muted">
            아직 생성된 PR 초안이 없습니다.
          </div>
        ) : (
          <>
            <div className="space-y-2.5">
              {drafts.map((item) => (
                <Link
                  className="focus-ring flex items-center justify-between rounded-xl border border-dark-border bg-dark-surface px-5 py-4 transition-all duration-200 hover:border-brand-500/60 hover:shadow-md hover:shadow-brand-500/5 group"
                  href={`/pr/${item.id}`}
                  key={item.id}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-dark-text group-hover:text-brand-300 transition-colors">{item.title}</p>
                    <p className="mt-1 font-mono text-xs text-dark-muted">
                      {item.upstreamRepo} · {formatDate(item.createdAt)}
                    </p>
                  </div>
                  <span className="ml-4 shrink-0 text-xs font-semibold text-brand-400 group-hover:translate-x-0.5 transition-transform">보기 →</span>
                </Link>
              ))}
            </div>

            {totalHistoryPages > 1 ? (
              <div className="mt-4 flex items-center justify-center gap-3">
                <button
                  className="focus-ring rounded-xl border border-dark-border bg-dark-surface px-3 py-1 text-xs font-semibold text-dark-muted hover:text-dark-text hover:bg-dark-elevated transition-colors disabled:opacity-30"
                  disabled={historyLoading || historyPage?.first}
                  onClick={() => setHistoryPageIndex((page) => Math.max(0, page - 1))}
                  type="button"
                >
                  ← 이전
                </button>
                <span className="text-xs font-semibold text-dark-muted">
                  {(historyPage?.page ?? 0) + 1} / {totalHistoryPages}
                </span>
                <button
                  className="focus-ring rounded-xl border border-dark-border bg-dark-surface px-3 py-1 text-xs font-semibold text-dark-muted hover:text-dark-text hover:bg-dark-elevated transition-colors disabled:opacity-30"
                  disabled={historyLoading || historyPage?.last}
                  onClick={() => setHistoryPageIndex((page) => page + 1)}
                  type="button"
                >
                  다음 →
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
      <div className="surface rounded-xl p-5 text-sm font-semibold text-dark-muted">불러오는 중...</div>
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

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
