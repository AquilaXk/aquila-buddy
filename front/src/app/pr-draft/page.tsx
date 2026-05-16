"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { FormEvent, Suspense, useMemo, useState } from "react";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { createPullRequestDraft, type StoredPullRequestDraft } from "@/lib/api";

type DraftInput = {
  upstreamRepo: string;
  githubIssueNumber: string;
  issueId: string;
  issueTitle: string;
  baseBranch: string;
  headBranch: string;
  diffSeed: string;
};

type SearchParamsLike = {
  get(name: string): string | null;
};

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
  const hasIssueParam = Boolean(initialDraftInput.upstreamRepo && Number(initialDraftInput.githubIssueNumber) > 0);
  const [baseBranch, setBaseBranch] = useState(initialDraftInput.baseBranch);
  const [headBranch, setHeadBranch] = useState(initialDraftInput.headBranch);
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (!hasIssueParam || !baseBranch.trim() || !headBranch.trim()) {
      return;
    }

    setSubmitting(true);
    setError(null);
    setDraft(null);

    try {
      setDraft(await createPullRequestDraft({
        upstreamRepo: initialDraftInput.upstreamRepo,
        githubIssueNumber: Number(initialDraftInput.githubIssueNumber),
        baseBranch: baseBranch.trim(),
        headBranch: headBranch.trim(),
        diffContent: initialDraftInput.diffSeed
      }));
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

  const canSubmit = hasIssueParam && baseBranch.trim() && headBranch.trim();

  return (
    <ProtectedLayout>
      <div className="mb-6 flex items-center gap-3">
        {hasIssueParam && initialDraftInput.issueId ? (
          <Link
            className="focus-ring text-sm font-semibold text-github-muted hover:text-github-text"
            href={`/issues/${encodeURIComponent(initialDraftInput.issueId)}`}
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
          {hasIssueParam ? (
            <section className="surface p-5">
              <p className="text-xs font-semibold text-github-muted">선택된 이슈</p>
              <p className="mt-2 break-all text-xs font-semibold text-github-muted">
                {initialDraftInput.upstreamRepo} #{initialDraftInput.githubIssueNumber}
              </p>
              <h2 className="mt-2 text-base font-semibold leading-6 text-github-text">
                {initialDraftInput.issueTitle || `Issue #${initialDraftInput.githubIssueNumber}`}
              </h2>
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
                    onClick={() => copyDraft(draft.title, "title")}
                    type="button"
                  >
                    {copied === "title" ? "복사됨 ✓" : "복사"}
                  </button>
                </div>
                <p className="text-sm font-semibold leading-6 text-github-text">{draft.title}</p>
              </div>

              <div className="rounded-md border border-github-border bg-github-bg p-4">
                <div className="mb-2 flex items-center justify-between gap-3">
                  <p className="text-xs font-semibold text-github-muted">PR 본문</p>
                  <button
                    className="focus-ring text-xs font-semibold text-github-muted hover:text-github-accent"
                    onClick={() => copyDraft(draft.body, "body")}
                    type="button"
                  >
                    {copied === "body" ? "복사됨 ✓" : "복사"}
                  </button>
                </div>
                <pre className="max-h-64 overflow-auto whitespace-pre-wrap text-xs leading-6 text-github-text">
                  {draft.body}
                </pre>
              </div>

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

  return {
    upstreamRepo,
    githubIssueNumber,
    issueId: readTextParam(searchParams, "issueId", readTextParam(searchParams, "sourceIssueId", "")),
    issueTitle,
    baseBranch: readTextParam(searchParams, "base", "main"),
    headBranch: readTextParam(searchParams, "head", ""),
    diffSeed: readTextParam(searchParams, "diff", buildDiffSeed(upstreamRepo, githubIssueNumber, issueTitle))
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
