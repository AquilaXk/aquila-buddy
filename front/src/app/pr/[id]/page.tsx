"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import { getPullRequestDraft, type StoredPullRequestDraft } from "@/lib/api";

export default function PrDetailPage() {
  const params = useParams<{ id: string }>();
  const prId = params.id;
  const [draft, setDraft] = useState<StoredPullRequestDraft | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

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
      <div className="mb-6">
        <Link
          className="focus-ring text-sm font-semibold text-github-muted hover:text-github-text"
          href="/pr-draft"
        >
          ← PR 초안 목록
        </Link>
      </div>

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
    </ProtectedLayout>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
