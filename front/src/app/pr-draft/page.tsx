"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  createPullRequestDraft,
  getPullRequestDraftHistory,
  type PullRequestDraftHistoryPage,
  type StoredPullRequestDraft
} from "@/lib/api";

type DraftInput = {
  upstreamRepo: string;
  githubIssueNumber: string;
  baseBranch: string;
  headBranch: string;
  diffContent: string;
};

type SearchParamsLike = {
  get(name: string): string | null;
};

const defaultDraftInput: DraftInput = {
  upstreamRepo: "spring-projects/spring-boot",
  githubIssueNumber: "2",
  baseBranch: "main",
  headBranch: "feat/validation-docs",
  diffContent: "diff --git a/docs.adoc b/docs.adoc\n+Add validation example"
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
  const sourceIssueId = searchParams.get("sourceIssueId");
  const hasDraftPrefill = hasDraftPrefillSearchParams(searchParams);
  const [upstreamRepo, setUpstreamRepo] = useState(initialDraftInput.upstreamRepo);
  const [githubIssueNumber, setGithubIssueNumber] = useState(initialDraftInput.githubIssueNumber);
  const [baseBranch, setBaseBranch] = useState(initialDraftInput.baseBranch);
  const [headBranch, setHeadBranch] = useState(initialDraftInput.headBranch);
  const [diffContent, setDiffContent] = useState(initialDraftInput.diffContent);
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [historyPageIndex, setHistoryPageIndex] = useState(0);
  const [historyPage, setHistoryPage] = useState<PullRequestDraftHistoryPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);

  const loadDraftHistory = useCallback(async (page: number) => {
    setLoadingHistory(true);

    try {
      const loadedHistoryPage = await getPullRequestDraftHistory(page, historyPageSize);
      setHistoryPage(loadedHistoryPage);
      setHistoryPageIndex(loadedHistoryPage.page);
      setHistoryError(null);
    } catch (caught) {
      setHistoryError(caught instanceof Error ? caught.message : "PR 초안 이력을 불러오지 못했습니다.");
    } finally {
      setLoadingHistory(false);
    }
  }, []);

  useEffect(() => {
    loadDraftHistory(historyPageIndex);
  }, [historyPageIndex, loadDraftHistory]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setDraft(null);

    try {
      const createdDraft = await createPullRequestDraft({
        upstreamRepo: upstreamRepo.trim(),
        githubIssueNumber: Number(githubIssueNumber),
        baseBranch: baseBranch.trim(),
        headBranch: headBranch.trim(),
        diffContent
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

  async function copyDraft(text: string, type: "title" | "body") {
    await navigator.clipboard.writeText(text);
    setCopied(type);
    window.setTimeout(() => setCopied(null), 1600);
  }

  const canSubmit = upstreamRepo.trim()
    && Number(githubIssueNumber) > 0
    && baseBranch.trim()
    && headBranch.trim()
    && diffContent.trim();
  const drafts = historyPage?.content ?? [];
  const totalDrafts = historyPage?.totalElements ?? 0;
  const currentHistoryPage = historyPage ? historyPage.page + 1 : historyPageIndex + 1;
  const totalHistoryPages = Math.max(historyPage?.totalPages ?? 1, 1);

  return (
    <ProtectedLayout>
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(360px,0.8fr)]">
        <form className="surface space-y-4 p-5" onSubmit={handleSubmit}>
          {hasDraftPrefill ? (
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-github-border bg-github-bg px-3 py-2">
              <div>
                <p className="text-sm font-semibold text-github-text">
                  이슈 상세에서 가져온 입력값입니다.
                </p>
                <p className="mt-1 break-all text-xs font-semibold text-github-muted">
                  {upstreamRepo} #{githubIssueNumber}
                </p>
              </div>
              {sourceIssueId ? (
                <Link
                  className="focus-ring rounded-md border border-github-border px-3 py-1.5 text-xs font-semibold text-github-muted hover:border-github-accent hover:text-github-accent"
                  href={`/issues/${encodeURIComponent(sourceIssueId)}`}
                >
                  Issue Guide
                </Link>
              ) : null}
            </div>
          ) : null}

          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-sm font-semibold">Repository</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2"
                onChange={(event) => setUpstreamRepo(event.target.value)}
                placeholder="owner/repo"
                value={upstreamRepo}
              />
            </label>
            <label className="block">
              <span className="text-sm font-semibold">Issue Number</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2"
                min="1"
                onChange={(event) => setGithubIssueNumber(event.target.value)}
                type="number"
                value={githubIssueNumber}
              />
            </label>
          </div>

          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-sm font-semibold">Base Branch</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2 font-mono text-sm"
                onChange={(event) => setBaseBranch(event.target.value)}
                placeholder="main"
                value={baseBranch}
              />
            </label>
            <label className="block">
              <span className="text-sm font-semibold">Head Branch</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2 font-mono text-sm"
                onChange={(event) => setHeadBranch(event.target.value)}
                placeholder="feat/my-change"
                value={headBranch}
              />
            </label>
          </div>

          <label className="block">
            <span className="text-sm font-semibold">Diff</span>
            <textarea
              className="focus-ring mt-2 min-h-52 w-full resize-y rounded-md border border-github-border px-3 py-2 font-mono text-sm leading-6"
              onChange={(event) => setDiffContent(event.target.value)}
              placeholder="git diff 내용을 입력"
              value={diffContent}
            />
          </label>

          {error ? (
            <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
              {error}
            </div>
          ) : null}

          <button
            className="focus-ring rounded-md bg-github-accent px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
            disabled={submitting || !canSubmit}
            type="submit"
          >
            {submitting ? "생성 중..." : "초안 생성"}
          </button>
        </form>

        <div className="space-y-5">
          <section className="surface min-h-80 p-5">
            {draft ? (
              <div className="space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div>
                    <p className="text-xs font-semibold text-github-accent">
                      {draft.upstreamRepo} #{draft.githubIssueNumber}
                    </p>
                    <Link
                      className="focus-ring mt-1 inline-block break-all text-sm font-semibold text-github-muted hover:text-github-accent"
                      href={`/pr/${draft.id}`}
                    >
                      Draft #{draft.id}
                    </Link>
                  </div>
                  <span className="text-xs font-semibold text-github-muted">
                    {formatDate(draft.createdAt)}
                  </span>
                </div>

                <div>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <h2 className="text-sm font-semibold">Title</h2>
                    <button
                      className="focus-ring rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted"
                      onClick={() => copyDraft(draft.title, "title")}
                      type="button"
                    >
                      {copied === "title" ? "복사됨" : "복사"}
                    </button>
                  </div>
                  <p className="rounded-md bg-github-bg p-3 text-sm font-semibold leading-6 text-github-text">
                    {draft.title}
                  </p>
                </div>

                <div>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <h2 className="text-sm font-semibold">Body</h2>
                    <button
                      className="focus-ring rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted"
                      onClick={() => copyDraft(draft.body, "body")}
                      type="button"
                    >
                      {copied === "body" ? "복사됨" : "복사"}
                    </button>
                  </div>
                  <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md bg-github-bg p-3 text-sm leading-6 text-github-text">
                    {draft.body}
                  </pre>
                </div>
              </div>
            ) : (
              <div className="flex h-full min-h-72 items-center justify-center text-center text-sm leading-6 text-github-muted">
                입력값을 채우고 초안을 생성하면 제목과 본문이 여기에 표시됩니다.
              </div>
            )}
          </section>

          <section className="surface p-5">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <h2 className="text-sm font-bold text-github-text">Recent drafts</h2>
              <span className="text-xs font-semibold text-github-muted">{totalDrafts} drafts</span>
            </div>
            {historyError ? (
              <div className="mt-4 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
                {historyError}
              </div>
            ) : null}
            {loadingHistory ? (
              <p className="mt-4 text-sm font-semibold text-github-muted">불러오는 중...</p>
            ) : null}
            {!loadingHistory && drafts.length === 0 ? (
              <p className="mt-4 text-sm font-semibold text-github-muted">저장된 PR 초안이 없습니다.</p>
            ) : null}
            <div className="mt-4 space-y-3">
              {drafts.map((item) => (
                <Link
                  className="focus-ring block rounded-md border border-github-border p-3 hover:border-github-accent"
                  href={`/pr/${item.id}`}
                  key={item.id}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="break-all text-xs font-semibold text-github-accent">
                      {item.upstreamRepo} #{item.githubIssueNumber}
                    </p>
                    <span className="text-xs font-semibold text-github-muted">
                      {formatDate(item.createdAt)}
                    </span>
                  </div>
                  <h3 className="mt-2 line-clamp-2 text-sm font-bold leading-5 text-github-text">
                    {item.title}
                  </h3>
                  <p className="mt-2 break-all font-mono text-xs text-github-muted">
                    {item.baseBranch} -&gt; {item.headBranch}
                  </p>
                </Link>
              ))}
            </div>
            {historyPage ? (
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-github-border pt-4">
                <button
                  className="focus-ring rounded-md border border-github-border px-3 py-2 text-sm font-semibold text-github-muted hover:border-github-accent hover:text-github-accent disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={loadingHistory || historyPage.first}
                  onClick={() => setHistoryPageIndex((page) => Math.max(0, page - 1))}
                  type="button"
                >
                  이전
                </button>
                <span className="text-sm font-semibold text-github-muted">
                  {currentHistoryPage} / {totalHistoryPages}
                </span>
                <button
                  className="focus-ring rounded-md border border-github-border px-3 py-2 text-sm font-semibold text-github-muted hover:border-github-accent hover:text-github-accent disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={loadingHistory || historyPage.last}
                  onClick={() => setHistoryPageIndex((page) => page + 1)}
                  type="button"
                >
                  다음
                </button>
              </div>
            ) : null}
          </section>
        </div>
      </div>
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
  return {
    upstreamRepo: readTextParam(searchParams, "repo", defaultDraftInput.upstreamRepo),
    githubIssueNumber: readIssueNumberParam(searchParams),
    baseBranch: readTextParam(searchParams, "base", defaultDraftInput.baseBranch),
    headBranch: readTextParam(searchParams, "head", defaultDraftInput.headBranch),
    diffContent: readTextParam(searchParams, "diff", defaultDraftInput.diffContent)
  };
}

function readTextParam(searchParams: SearchParamsLike, name: string, fallback: string): string {
  const value = searchParams.get(name);

  return value && value.trim() ? value : fallback;
}

function readIssueNumberParam(searchParams: SearchParamsLike): string {
  const value = searchParams.get("issue");
  const issueNumber = Number(value);

  if (!Number.isInteger(issueNumber) || issueNumber <= 0) {
    return defaultDraftInput.githubIssueNumber;
  }

  return String(issueNumber);
}

function hasDraftPrefillSearchParams(searchParams: SearchParamsLike): boolean {
  return ["repo", "issue", "base", "head", "diff", "sourceIssueId"].some((name) => {
    const value = searchParams.get(name);

    return Boolean(value && value.trim());
  });
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
