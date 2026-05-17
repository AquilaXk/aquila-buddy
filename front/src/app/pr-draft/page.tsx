"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  createPullRequestDraft,
  getIssueDetail,
  getPullRequestDraftHistory,
  translatePullRequestDraft,
  type PullRequestDraftHistoryPage,
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
  diffSeed: string;
  hasExplicitDiffSeed: boolean;
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
        headBranch: headBranch.trim(),
        diffContent: draftInput.diffSeed
      });

      setDraft(createdDraft);
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
      setTranslated(await translatePullRequestDraft(draft.id));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "번역에 실패했습니다.");
    } finally {
      setTranslating(false);
    }
  }

  async function copyDraft(text: string, type: "title" | "body") {
    await navigator.clipboard.writeText(text);
    setCopied(type);
    window.setTimeout(() => setCopied(null), 1600);
  }

  const canSubmit = hasIssueParam && baseBranch.trim() && headBranch.trim();
  const displayTitle = translated?.titleEn ?? draft?.title ?? "";
  const displayBody = translated?.bodyEn ?? draft?.body ?? "";
  const drafts = historyPage?.content ?? [];
  const totalHistoryPages = Math.max(historyPage?.totalPages ?? 1, 1);

  return (
    <ProtectedLayout>
      <div className="mb-6 flex items-center gap-3">
        {draftInput.issueId ? (
          <Link
            className="focus-ring text-sm font-semibold text-github-muted hover:text-github-text"
            href={`/issues/${encodeURIComponent(draftInput.issueId)}`}
          >
            ← 이슈로 돌아가기
          </Link>
        ) : (
          <Link
            className="focus-ring text-sm font-semibold text-github-muted hover:text-github-text"
            href="/issues"
          >
            ← 추천 이슈
          </Link>
        )}
        <h1 className="text-2xl font-bold text-github-text">PR 초안 생성</h1>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="space-y-4">
          {hasIssueParam || draftInput.issueId ? (
            <section className="surface p-5">
              <p className="text-xs font-semibold text-github-muted">선택된 이슈</p>
              <p className="mt-2 break-all text-xs font-semibold text-github-muted">
                {hasIssueParam ? `${draftInput.upstreamRepo} #${draftInput.githubIssueNumber}` : "이슈 정보를 불러오는 중..."}
              </p>
              <h2 className="mt-2 text-base font-semibold leading-6 text-github-text">
                {draftInput.issueTitle || (hasIssueParam ? `Issue #${draftInput.githubIssueNumber}` : draftInput.issueId)}
              </h2>
              {sourceIssueLoading ? (
                <p className="mt-3 text-xs font-semibold text-github-muted">이슈 정보를 불러오는 중...</p>
              ) : null}
              {sourceIssueError ? (
                <p className="mt-3 text-xs font-semibold text-yellow-300">{sourceIssueError}</p>
              ) : null}
            </section>
          ) : (
            <section className="surface border-yellow-500/30 bg-yellow-500/10 p-5">
              <p className="text-sm font-semibold text-yellow-300">
                이슈 상세 페이지에서 "PR 초안 만들기"를 통해 접근해주세요.{" "}
                <Link className="underline" href="/issues">
                  이슈 목록으로
                </Link>
              </p>
            </section>
          )}

          {hasIssueParam ? (
            <form className="space-y-4" onSubmit={handleSubmit}>
              <label className="block">
                <span className="text-xs font-semibold text-github-muted">
                  Base 브랜치 <span className="text-red-400">*</span>
                </span>
                <input
                  className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2 font-mono text-sm"
                  onChange={(event) => setBaseBranch(event.target.value)}
                  placeholder="main"
                  value={baseBranch}
                />
                <span className="mt-1 block text-xs font-semibold text-github-muted">
                  PR의 대상 브랜치입니다.
                </span>
              </label>

              <label className="block">
                <span className="text-xs font-semibold text-github-muted">
                  Head 브랜치 <span className="text-red-400">*</span>
                </span>
                <input
                  className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2 font-mono text-sm"
                  onChange={(event) => setHeadBranch(event.target.value)}
                  placeholder="feat/my-feature"
                  value={headBranch}
                />
                <span className="mt-1 block text-xs font-semibold text-github-muted">
                  내 작업 브랜치입니다.
                </span>
              </label>

              {error ? (
                <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
                  {error}
                </div>
              ) : null}

              <button
                className="focus-ring flex w-full items-center justify-center rounded-md bg-github-purple px-4 py-3 text-sm font-semibold text-white hover:bg-github-purple/90 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={submitting || !canSubmit}
                type="submit"
              >
                {submitting ? "AI가 PR 초안 생성 중..." : "PR 초안 생성"}
              </button>
            </form>
          ) : null}
        </div>

        <section className="surface min-h-80 p-5">
          {draft ? (
            <div className="space-y-4">
              <h2 className="text-base font-semibold text-github-text">생성된 PR 초안</h2>

              <div className="rounded-md border border-github-border bg-github-bg p-4">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold text-github-muted">PR 제목</p>
                  <button
                    className="focus-ring text-xs font-semibold text-github-muted hover:text-github-accent"
                    onClick={() => copyDraft(displayTitle, "title")}
                    type="button"
                  >
                    {copied === "title" ? "복사됨 ✓" : "복사"}
                  </button>
                </div>
                <p className="text-sm font-semibold leading-6 text-github-text">{displayTitle}</p>
              </div>

              <div className="rounded-md border border-github-border bg-github-bg p-4">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold text-github-muted">PR 본문</p>
                  <button
                    className="focus-ring text-xs font-semibold text-github-muted hover:text-github-accent"
                    onClick={() => copyDraft(displayBody, "body")}
                    type="button"
                  >
                    {copied === "body" ? "복사됨 ✓" : "복사"}
                  </button>
                </div>
                <pre className="max-h-64 overflow-auto whitespace-pre-wrap text-xs leading-6 text-github-text">
                  {displayBody}
                </pre>
              </div>

              {!translated ? (
                <button
                  className="focus-ring flex w-full items-center justify-center rounded-md border border-github-accent/30 bg-github-accent/10 px-4 py-2.5 text-sm font-semibold text-github-accent hover:bg-github-accent/20 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={translating}
                  onClick={handleTranslate}
                  type="button"
                >
                  {translating ? "영어로 번역 중..." : "영어로 번역하기"}
                </button>
              ) : (
                <a
                  className="focus-ring flex w-full items-center justify-center rounded-md border border-github-accent/30 bg-github-accent/10 px-4 py-3 text-sm font-semibold text-github-accent hover:bg-github-accent/20"
                  href={translated.githubUrl}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  GitHub에서 PR 올리기 ↗
                </a>
              )}

              <Link
                className="focus-ring block text-center text-xs font-semibold text-github-muted hover:text-github-text"
                href={`/pr/${draft.id}`}
              >
                상세 보기 (수정·삭제) →
              </Link>
            </div>
          ) : (
            <div className="flex h-full min-h-72 items-center justify-center text-center text-sm leading-6 text-github-muted">
              선택한 이슈의 브랜치 정보를 입력하면 PR 초안 결과가 여기에 표시됩니다.
            </div>
          )}
        </section>
      </div>

      <section className="mt-10">
        <h2 className="mb-4 font-semibold text-github-text">PR 초안 이력</h2>
        {historyError ? (
          <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
            {historyError}
          </div>
        ) : null}
        {historyLoading ? (
          <div className="space-y-2">
            <div className="surface h-16 animate-pulse" />
            <div className="surface h-16 animate-pulse" />
            <div className="surface h-16 animate-pulse" />
          </div>
        ) : drafts.length === 0 ? (
          <div className="py-8 text-center text-sm font-semibold text-github-muted">
            아직 생성된 PR 초안이 없습니다.
          </div>
        ) : (
          <>
            <div className="space-y-2">
              {drafts.map((item) => (
                <Link
                  className="focus-ring flex items-center justify-between rounded-md border border-github-border bg-github-surface px-5 py-4 hover:border-github-purple/50"
                  href={`/pr/${item.id}`}
                  key={item.id}
                >
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-github-text">{item.title}</p>
                    <p className="mt-1 text-xs font-semibold text-github-muted">
                      {item.upstreamRepo} · {formatDate(item.createdAt)}
                    </p>
                  </div>
                  <span className="ml-4 shrink-0 text-xs font-semibold text-github-muted">보기 →</span>
                </Link>
              ))}
            </div>

            {totalHistoryPages > 1 ? (
              <div className="mt-4 flex items-center justify-center gap-3">
                <button
                  className="focus-ring rounded-md border border-github-border px-3 py-1 text-xs font-semibold text-github-muted hover:text-github-text disabled:opacity-30"
                  disabled={historyLoading || historyPage?.first}
                  onClick={() => setHistoryPageIndex((page) => Math.max(0, page - 1))}
                  type="button"
                >
                  ← 이전
                </button>
                <span className="text-xs font-semibold text-github-muted">
                  {(historyPage?.page ?? 0) + 1} / {totalHistoryPages}
                </span>
                <button
                  className="focus-ring rounded-md border border-github-border px-3 py-1 text-xs font-semibold text-github-muted hover:text-github-text disabled:opacity-30"
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
      <div className="surface p-5 text-sm font-semibold text-github-muted">불러오는 중...</div>
    </ProtectedLayout>
  );
}

function readDraftInputFromSearchParams(searchParams: SearchParamsLike): DraftInput {
  const upstreamRepo = readTextParam(searchParams, "repo", "");
  const githubIssueNumber = readIssueNumberParam(searchParams);
  const issueTitle = readTextParam(searchParams, "issueTitle", "");
  const explicitDiffSeed = readTextParam(searchParams, "diff", "");

  return {
    upstreamRepo,
    githubIssueNumber,
    issueId: readTextParam(searchParams, "issueId", readTextParam(searchParams, "sourceIssueId", "")),
    issueTitle,
    baseBranch: readTextParam(searchParams, "base", "main"),
    headBranch: readTextParam(searchParams, "head", ""),
    diffSeed: explicitDiffSeed || buildDiffSeed(upstreamRepo, githubIssueNumber, issueTitle),
    hasExplicitDiffSeed: Boolean(explicitDiffSeed)
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
    issueTitle,
    diffSeed: currentInput.hasExplicitDiffSeed
      ? currentInput.diffSeed
      : buildDiffSeed(upstreamRepo, githubIssueNumber, issueTitle)
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

function buildDiffSeed(upstreamRepo: string, issueNumber: string, issueTitle: string) {
  return [
    `diff --git a/ISSUE-${issueNumber || "draft"}.md b/ISSUE-${issueNumber || "draft"}.md`,
    `+${upstreamRepo} #${issueNumber}`,
    `+${issueTitle || "PR draft context"}`
  ].join("\n");
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
