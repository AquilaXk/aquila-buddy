"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import {
  deletePullRequestDraft,
  updatePullRequestDraft,
  type StoredPullRequestDraft,
  type UpdatePullRequestDraftRequest
} from "@/lib/api";

type PrDraftEditPanelProps = {
  draft: StoredPullRequestDraft;
};

export function PrDraftEditPanel({ draft }: PrDraftEditPanelProps) {
  const router = useRouter();
  const [currentDraft, setCurrentDraft] = useState(draft);
  const [title, setTitle] = useState(draft.title);
  const [body, setBody] = useState(draft.body);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const canSave = Boolean(title.trim() && body.trim())
    && (title !== currentDraft.title || body !== currentDraft.body);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    setMessage(null);

    const request: UpdatePullRequestDraftRequest = {
      title: title.trim(),
      body: body.trim()
    };

    try {
      const updatedDraft = await updatePullRequestDraft(currentDraft.id, request);
      setCurrentDraft(updatedDraft);
      setTitle(updatedDraft.title);
      setBody(updatedDraft.body);
      setMessage("PR 초안을 저장했습니다.");
      router.refresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "PR 초안 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    const confirmed = window.confirm("PR 초안을 삭제할까요?");
    if (!confirmed) {
      return;
    }

    setDeleting(true);
    setError(null);
    setMessage(null);

    try {
      await deletePullRequestDraft(currentDraft.id);
      router.push("/pr-draft");
      router.refresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "PR 초안 삭제에 실패했습니다.");
      setDeleting(false);
    }
  }

  return (
    <section className="rounded-md border border-github-border p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-github-accent">Draft Editor</p>
          <h3 className="mt-1 text-lg font-bold text-github-text">제목과 본문</h3>
        </div>
        <button
          className="focus-ring rounded-md border border-red-500/30 px-3 py-2 text-sm font-semibold text-red-400 hover:bg-red-500/10 disabled:cursor-not-allowed disabled:opacity-50"
          disabled={deleting || saving}
          onClick={handleDelete}
          type="button"
        >
          {deleting ? "삭제 중..." : "삭제"}
        </button>
      </div>

      <form className="mt-4 space-y-4" onSubmit={handleSubmit}>
        <label className="block">
          <span className="text-sm font-semibold">Title</span>
          <input
            className="focus-ring mt-2 w-full rounded-md border border-github-border px-3 py-2 text-sm font-semibold"
            onChange={(event) => setTitle(event.target.value)}
            value={title}
          />
        </label>

        <label className="block">
          <span className="text-sm font-semibold">Body</span>
          <textarea
            className="focus-ring mt-2 min-h-72 w-full resize-y rounded-md border border-github-border px-3 py-2 font-mono text-sm leading-6"
            onChange={(event) => setBody(event.target.value)}
            value={body}
          />
        </label>

        {error ? (
          <div className="rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
            {error}
          </div>
        ) : null}
        {message ? (
          <div className="rounded-md border border-green-500/30 bg-green-500/10 px-3 py-2 text-sm font-semibold text-green-400">
            {message}
          </div>
        ) : null}

        <button
          className="focus-ring rounded-md bg-github-accent px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          disabled={!canSave || saving || deleting}
          type="submit"
        >
          {saving ? "저장 중..." : "저장"}
        </button>
      </form>
    </section>
  );
}
