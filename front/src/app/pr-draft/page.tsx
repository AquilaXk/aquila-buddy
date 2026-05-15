"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  createPullRequestDraft,
  getPullRequestDrafts,
  type StoredPullRequestDraft
} from "@/lib/api";

export default function PrDraftPage() {
  const [upstreamRepo, setUpstreamRepo] = useState("spring-projects/spring-boot");
  const [githubIssueNumber, setGithubIssueNumber] = useState("2");
  const [baseBranch, setBaseBranch] = useState("main");
  const [headBranch, setHeadBranch] = useState("feat/validation-docs");
  const [diffContent, setDiffContent] = useState("diff --git a/docs.adoc b/docs.adoc\n+Add validation example");
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [drafts, setDrafts] = useState<StoredPullRequestDraft[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);

  useEffect(() => {
    let mounted = true;

    async function loadDrafts() {
      try {
        const loadedDrafts = await getPullRequestDrafts();
        if (mounted) {
          setDrafts(loadedDrafts);
          setHistoryError(null);
        }
      } catch (caught) {
        if (mounted) {
          setHistoryError(caught instanceof Error ? caught.message : "PR 초안 이력을 불러오지 못했습니다.");
        }
      } finally {
        if (mounted) {
          setLoadingHistory(false);
        }
      }
    }

    loadDrafts();

    return () => {
      mounted = false;
    };
  }, []);

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
      setDrafts((currentDrafts) => [
        createdDraft,
        ...currentDrafts.filter((currentDraft) => currentDraft.id !== createdDraft.id)
      ]);
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

  return (
    <ProtectedLayout title="PR Draft">
      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(360px,0.8fr)]">
        <form className="surface space-y-4 p-5" onSubmit={handleSubmit}>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="block">
              <span className="text-sm font-semibold">Repository</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2"
                onChange={(event) => setUpstreamRepo(event.target.value)}
                placeholder="owner/repo"
                value={upstreamRepo}
              />
            </label>
            <label className="block">
              <span className="text-sm font-semibold">Issue Number</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2"
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
                className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2 font-mono text-sm"
                onChange={(event) => setBaseBranch(event.target.value)}
                placeholder="main"
                value={baseBranch}
              />
            </label>
            <label className="block">
              <span className="text-sm font-semibold">Head Branch</span>
              <input
                className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2 font-mono text-sm"
                onChange={(event) => setHeadBranch(event.target.value)}
                placeholder="feat/my-change"
                value={headBranch}
              />
            </label>
          </div>

          <label className="block">
            <span className="text-sm font-semibold">Diff</span>
            <textarea
              className="focus-ring mt-2 min-h-52 w-full resize-y rounded-md border border-line px-3 py-2 font-mono text-sm leading-6"
              onChange={(event) => setDiffContent(event.target.value)}
              placeholder="git diff 내용을 입력"
              value={diffContent}
            />
          </label>

          {error ? (
            <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
              {error}
            </div>
          ) : null}

          <button
            className="focus-ring rounded-md bg-moss px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
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
                    <p className="text-xs font-semibold text-moss">
                      {draft.upstreamRepo} #{draft.githubIssueNumber}
                    </p>
                    <Link
                      className="focus-ring mt-1 inline-block break-all text-sm font-semibold text-neutral-700 hover:text-moss"
                      href={`/pr/${draft.id}`}
                    >
                      Draft #{draft.id}
                    </Link>
                  </div>
                  <span className="text-xs font-semibold text-neutral-500">
                    {formatDate(draft.createdAt)}
                  </span>
                </div>

                <div>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <h2 className="text-sm font-semibold">Title</h2>
                    <button
                      className="focus-ring rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-700"
                      onClick={() => copyDraft(draft.title, "title")}
                      type="button"
                    >
                      {copied === "title" ? "복사됨" : "복사"}
                    </button>
                  </div>
                  <p className="rounded-md bg-neutral-100 p-3 text-sm font-semibold leading-6 text-ink">
                    {draft.title}
                  </p>
                </div>

                <div>
                  <div className="mb-2 flex items-center justify-between gap-3">
                    <h2 className="text-sm font-semibold">Body</h2>
                    <button
                      className="focus-ring rounded-md border border-line px-2 py-1 text-xs font-semibold text-neutral-700"
                      onClick={() => copyDraft(draft.body, "body")}
                      type="button"
                    >
                      {copied === "body" ? "복사됨" : "복사"}
                    </button>
                  </div>
                  <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md bg-neutral-100 p-3 text-sm leading-6 text-neutral-800">
                    {draft.body}
                  </pre>
                </div>
              </div>
            ) : (
              <div className="flex h-full min-h-72 items-center justify-center text-center text-sm leading-6 text-neutral-600">
                입력값을 채우고 초안을 생성하면 제목과 본문이 여기에 표시됩니다.
              </div>
            )}
          </section>

          <section className="surface p-5">
            <div className="flex flex-wrap items-end justify-between gap-3">
              <h2 className="text-sm font-bold text-neutral-900">Recent drafts</h2>
              <span className="text-xs font-semibold text-neutral-500">{drafts.length} drafts</span>
            </div>
            {historyError ? (
              <div className="mt-4 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
                {historyError}
              </div>
            ) : null}
            {loadingHistory ? (
              <p className="mt-4 text-sm font-semibold text-neutral-600">불러오는 중...</p>
            ) : null}
            {!loadingHistory && drafts.length === 0 ? (
              <p className="mt-4 text-sm font-semibold text-neutral-600">저장된 PR 초안이 없습니다.</p>
            ) : null}
            <div className="mt-4 space-y-3">
              {drafts.map((item) => (
                <Link
                  className="focus-ring block rounded-md border border-line p-3 hover:border-moss"
                  href={`/pr/${item.id}`}
                  key={item.id}
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="break-all text-xs font-semibold text-moss">
                      {item.upstreamRepo} #{item.githubIssueNumber}
                    </p>
                    <span className="text-xs font-semibold text-neutral-500">
                      {formatDate(item.createdAt)}
                    </span>
                  </div>
                  <h3 className="mt-2 line-clamp-2 text-sm font-bold leading-5 text-ink">
                    {item.title}
                  </h3>
                  <p className="mt-2 break-all font-mono text-xs text-neutral-500">
                    {item.baseBranch} -&gt; {item.headBranch}
                  </p>
                </Link>
              ))}
            </div>
          </section>
        </div>
      </div>
    </ProtectedLayout>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
