"use client";

import { FormEvent, useState } from "react";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { createPullRequestDraft, PullRequestDraft } from "@/lib/api";

export default function PrDraftPage() {
  const [upstreamRepo, setUpstreamRepo] = useState("spring-projects/spring-boot");
  const [githubIssueNumber, setGithubIssueNumber] = useState("2");
  const [baseBranch, setBaseBranch] = useState("main");
  const [headBranch, setHeadBranch] = useState("feat/validation-docs");
  const [diffContent, setDiffContent] = useState("diff --git a/docs.adoc b/docs.adoc\n+Add validation example");
  const [draft, setDraft] = useState<PullRequestDraft | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);

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

        <section className="surface min-h-80 p-5">
          {draft ? (
            <div className="space-y-4">
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
      </div>
    </ProtectedLayout>
  );
}
