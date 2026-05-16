"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  deletePullRequestDraft,
  getPullRequestDraft,
  updatePullRequestDraft,
  type StoredPullRequestDraft
} from "@/lib/api";

export default function PrDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const prId = params.id;
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editBody, setEditBody] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [copied, setCopied] = useState<"title" | "body" | null>(null);
  const [error, setError] = useState<string | null>(null);

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
      } catch {
        if (!isMounted) {
          return;
        }

        setDraft(null);
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
        body: editBody
      });
      setDraft(updatedDraft);
      setEditing(false);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete() {
    if (!window.confirm("PR 초안을 삭제하시겠습니까?")) {
      return;
    }

    setDeleting(true);
    setError(null);

    try {
      await deletePullRequestDraft(prId);
      router.push("/pr-draft");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "삭제에 실패했습니다.");
      setDeleting(false);
    }
  }

  async function copyToClipboard(text: string, type: "title" | "body") {
    await navigator.clipboard.writeText(text);
    setCopied(type);
    window.setTimeout(() => setCopied(null), 2000);
  }

  function cancelEdit() {
    if (!draft) {
      return;
    }

    setEditing(false);
    setEditTitle(draft.title);
    setEditBody(draft.body);
  }

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="surface h-20 animate-pulse" />
          <div className="surface h-28 animate-pulse" />
          <div className="surface h-40 animate-pulse" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !draft) {
    return (
      <ProtectedLayout>
        <section className="surface py-20 text-center">
          <h1 className="text-2xl font-bold text-github-text">PR 초안을 찾을 수 없습니다.</h1>
          <p className="mt-3 text-sm font-semibold text-github-muted">
            저장된 PR 초안 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-6 inline-flex text-sm font-semibold text-github-accent"
            href="/pr-draft"
          >
            ← PR 초안 목록으로
          </Link>
        </section>
      </ProtectedLayout>
    );
  }

  return (
    <ProtectedLayout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <Link
          className="focus-ring text-sm font-semibold text-github-muted hover:text-github-text"
          href="/pr-draft"
        >
          ← PR 초안 목록
        </Link>
        <div className="flex items-center gap-2">
          {!editing ? (
            <button
              className="focus-ring rounded-md border border-github-border bg-github-surface px-3 py-1.5 text-xs font-semibold text-github-muted hover:text-github-text"
              onClick={() => setEditing(true)}
              type="button"
            >
              편집
            </button>
          ) : null}
          <button
            className="focus-ring rounded-md border border-red-500/20 bg-red-500/10 px-3 py-1.5 text-xs font-semibold text-red-400 hover:bg-red-500/20 hover:text-red-300 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={deleting}
            onClick={handleDelete}
            type="button"
          >
            {deleting ? "삭제 중..." : "삭제"}
          </button>
        </div>
      </div>

      {error ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          {error}
        </div>
      ) : null}

      <section className="surface mb-5 flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs font-semibold text-github-muted">연결된 이슈</p>
          <p className="mt-1 text-sm font-semibold text-github-text">
            {draft.upstreamRepo} #{draft.githubIssueNumber}
          </p>
          <p className="mt-1 break-all font-mono text-xs text-github-muted">
            {draft.baseBranch} -&gt; {draft.headBranch}
          </p>
        </div>
        <p className="text-xs font-semibold text-github-muted">{formatDate(draft.createdAt)}</p>
      </section>

      <section className="surface mb-4 p-5">
        <div className="mb-2 flex items-center justify-between gap-3">
          <p className="text-xs font-semibold text-github-muted">PR 제목</p>
          <button
            className="focus-ring text-xs font-semibold text-github-muted hover:text-github-accent"
            onClick={() => copyToClipboard(draft.title, "title")}
            type="button"
          >
            {copied === "title" ? "복사됨 ✓" : "복사"}
          </button>
        </div>
        {editing ? (
          <input
            className="focus-ring w-full rounded-md border border-github-border px-3 py-2 text-sm"
            onChange={(event) => setEditTitle(event.target.value)}
            value={editTitle}
          />
        ) : (
          <p className="text-sm font-semibold leading-6 text-github-text">{draft.title}</p>
        )}
      </section>

      <section className="surface mb-4 p-5">
        <div className="mb-2 flex items-center justify-between gap-3">
          <p className="text-xs font-semibold text-github-muted">PR 본문</p>
          <button
            className="focus-ring text-xs font-semibold text-github-muted hover:text-github-accent"
            onClick={() => copyToClipboard(draft.body, "body")}
            type="button"
          >
            {copied === "body" ? "복사됨 ✓" : "복사"}
          </button>
        </div>
        {editing ? (
          <textarea
            className="focus-ring min-h-72 w-full resize-y rounded-md border border-github-border px-3 py-2 font-mono text-xs leading-6"
            onChange={(event) => setEditBody(event.target.value)}
            value={editBody}
          />
        ) : (
          <pre className="max-h-72 overflow-y-auto whitespace-pre-wrap text-xs leading-6 text-github-text">
            {draft.body}
          </pre>
        )}
      </section>

      {editing ? (
        <div className="flex flex-wrap gap-3">
          <button
            className="focus-ring rounded-md bg-github-purple px-5 py-2.5 text-sm font-semibold text-white hover:bg-github-purple/90 disabled:cursor-not-allowed disabled:opacity-50"
            disabled={saving}
            onClick={handleSave}
            type="button"
          >
            {saving ? "저장 중..." : "저장"}
          </button>
          <button
            className="focus-ring rounded-md border border-github-border px-5 py-2.5 text-sm font-semibold text-github-muted hover:text-github-text"
            onClick={cancelEdit}
            type="button"
          >
            취소
          </button>
        </div>
      ) : null}
    </ProtectedLayout>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
