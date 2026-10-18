"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AlertCircle } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import {
  getPullRequestDraftHistory,
  getRecommendedIssueHistory,
  getUserProfile,
  type PullRequestDraftHistoryPage,
  type RecommendedIssueHistory,
  type UserProfile
} from "@/lib/api";
import { formatDate } from "@/lib/format";

export default function DashboardPage() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [issueHistory, setIssueHistory] = useState<RecommendedIssueHistory[]>([]);
  const [draftPage, setDraftPage] = useState<PullRequestDraftHistoryPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    Promise.allSettled([
      getUserProfile(),
      getRecommendedIssueHistory(),
      getPullRequestDraftHistory(0, 5)
    ])
      .then(([profileResult, issuesResult, draftsResult]) => {
        if (!isMounted) {
          return;
        }

        if (profileResult.status === "fulfilled") {
          setUser(profileResult.value);
          setError(null);
        } else {
          setUser(null);
          const reason = profileResult.reason;
          setError(reason instanceof Error ? reason.message : "프로필 정보를 불러오지 못했습니다.");
        }

        if (issuesResult.status === "fulfilled") {
          setIssueHistory(issuesResult.value);
        } else {
          setIssueHistory([]);
        }

        if (draftsResult.status === "fulfilled") {
          setDraftPage(draftsResult.value);
        } else {
          setDraftPage(null);
        }
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, []);

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="surface h-16 animate-pulse bg-neutral-subtle" />
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="surface h-72 animate-pulse bg-neutral-subtle lg:col-span-7" />
            <div className="surface h-72 animate-pulse bg-neutral-subtle lg:col-span-5" />
          </div>
        </div>
      </ProtectedLayout>
    );
  }

  const displayName = user?.name || user?.login || "사용자";
  const hasVector = Boolean(user?.profileReady || user?.vectorUpdatedAt);
  const recentIssues = issueHistory.slice(0, 6);
  const recentDrafts = draftPage?.content ?? [];
  const analyzedCount = issueHistory.filter((item) => item.isAnalyzed).length;

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-col justify-between gap-4 border-b border-neutral-border pb-5 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] text-ink-primary">
            안녕하세요, {displayName}님
          </h1>
          <p className="mt-1 text-xs text-ink-secondary">기여 워크스페이스 개요</p>
        </div>

        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="tabular-nums rounded border border-neutral-border bg-neutral-subtle px-2.5 py-1 font-mono text-ink-secondary">
            추천 이슈 <strong className="font-semibold text-ink-primary">{issueHistory.length}</strong>
          </span>
          <span className="tabular-nums rounded border border-neutral-border bg-neutral-subtle px-2.5 py-1 font-mono text-ink-secondary">
            코드 분석 <strong className="font-semibold text-ink-primary">{analyzedCount}</strong>
          </span>
          <span className="tabular-nums rounded border border-neutral-border bg-neutral-subtle px-2.5 py-1 font-mono text-ink-secondary">
            PR 초안 <strong className="font-semibold text-ink-primary">{draftPage?.totalElements ?? recentDrafts.length}</strong>
          </span>
        </div>
      </header>

      {error ? (
        <div className="mb-5 flex items-center gap-2 rounded-md border border-[#c5221f]/20 bg-[#fce8e6] px-4 py-3 text-sm font-medium text-[#c5221f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>프로필 정보를 불러오지 못했습니다: {error}</span>
        </div>
      ) : null}

      {!hasVector ? (
        <div className="mb-6 flex flex-col justify-between gap-3 rounded-md border border-[#b06000]/25 bg-[#fef7e0] px-4 py-3 sm:flex-row sm:items-center">
          <div className="flex items-start gap-2.5">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-[#b06000]" />
            <div>
              <p className="text-xs font-semibold text-[#b06000]">프로필 분석이 필요합니다</p>
              <p className="mt-0.5 text-xs text-ink-secondary">
                이슈 추천을 받으려면 먼저 마이페이지에서 벡터 업데이트를 완료해주세요.
              </p>
            </div>
          </div>
          <Link
            className="btn-primary focus-ring inline-flex shrink-0 items-center px-3 py-1.5 text-xs"
            href="/profile"
          >
            <span className="button-text">프로필로 이동</span>
          </Link>
        </div>
      ) : null}

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <section className="surface overflow-hidden lg:col-span-7">
          <div className="sheet-header flex items-center justify-between gap-3 px-4 py-3">
            <div className="flex items-center gap-2">
              <h2 className="text-xs font-semibold text-ink-primary">이슈 추천 작업 큐</h2>
              <span className="tabular-nums rounded bg-neutral-hover px-1.5 py-0.5 font-mono text-[11px] font-medium text-ink-secondary">
                {issueHistory.length}
              </span>
            </div>
            <Link
              className="focus-ring text-xs font-medium text-brand-500 transition-colors hover:text-brand-600"
              href="/issues"
            >
              전체 목록 및 추천 실행
            </Link>
          </div>

          {recentIssues.length > 0 ? (
            <div className="divide-y divide-neutral-border">
              {recentIssues.map((issue) => {
                const scorePercent = Math.round(issue.score * 100);
                return (
                  <Link
                    className="focus-ring group flex items-start justify-between gap-4 px-4 py-3 transition-colors hover:bg-neutral-subtle"
                    href={`/issues/${encodeURIComponent(issue.id)}`}
                    key={issue.id}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <StatusBadge status={issue.status} />
                        <span className="truncate font-mono text-xs text-ink-muted">
                          {issue.repoFullName} #{issue.issueNumber}
                        </span>
                      </div>
                      <p className="mt-1 truncate text-sm font-medium text-ink-primary transition-colors group-hover:text-brand-500">
                        {issue.title}
                      </p>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      {issue.score > 0 ? (
                        <span className="badge-trim tabular-nums rounded border border-[#137333]/20 bg-[#e6f4ea] px-2 py-0.5 font-mono text-[11px] font-medium text-[#137333]">
                          {scorePercent}%
                        </span>
                      ) : null}
                      <span className="text-[11px] text-ink-muted">
                        {issue.isAnalyzed ? "분석 완료" : "분석 전"}
                      </span>
                    </div>
                  </Link>
                );
              })}
            </div>
          ) : (
            <div className="px-4 py-10 text-center">
              <p className="text-xs text-ink-secondary">아직 저장된 추천 이슈가 없습니다.</p>
              <Link
                className="btn-secondary focus-ring mt-3 inline-flex items-center px-3 py-1.5 text-xs"
                href="/issues"
              >
                <span className="button-text">이슈 추천 시작하기</span>
              </Link>
            </div>
          )}
        </section>

        <div className="space-y-6 lg:col-span-5">
          <section className="surface overflow-hidden">
            <div className="sheet-header flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex items-center gap-2">
                <h2 className="text-xs font-semibold text-ink-primary">PR 초안 생성 이력</h2>
                <span className="tabular-nums rounded bg-neutral-hover px-1.5 py-0.5 font-mono text-[11px] font-medium text-ink-secondary">
                  {draftPage?.totalElements ?? recentDrafts.length}
                </span>
              </div>
              <Link
                className="focus-ring text-xs font-medium text-brand-500 transition-colors hover:text-brand-600"
                href="/pr-draft"
              >
                초안 작성소
              </Link>
            </div>

            {recentDrafts.length > 0 ? (
              <div className="divide-y divide-neutral-border">
                {recentDrafts.map((draft) => (
                  <Link
                    className="focus-ring group block px-4 py-3 transition-colors hover:bg-neutral-subtle"
                    href={`/pr/${encodeURIComponent(draft.id)}`}
                    key={draft.id}
                  >
                    <p className="truncate text-sm font-medium text-ink-primary transition-colors group-hover:text-brand-500">
                      {draft.title}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center justify-between gap-2 font-mono text-[11px] text-ink-muted">
                      <span className="truncate">
                        {draft.upstreamRepo} #{draft.githubIssueNumber}
                      </span>
                      <span className="tabular-nums shrink-0">{formatDate(draft.createdAt)}</span>
                    </div>
                  </Link>
                ))}
              </div>
            ) : (
              <div className="px-4 py-8 text-center">
                <p className="text-xs text-ink-secondary">생성된 PR 초안이 없습니다.</p>
              </div>
            )}
          </section>

          <section className="surface overflow-hidden">
            <div className="sheet-header flex items-center justify-between gap-3 px-4 py-3">
              <h2 className="text-xs font-semibold text-ink-primary">내 계정 정보</h2>
              <Link
                className="focus-ring text-xs font-medium text-brand-500 transition-colors hover:text-brand-600"
                href="/profile"
              >
                프로필 편집
              </Link>
            </div>

            <dl className="divide-y divide-neutral-border text-xs">
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="text-ink-muted">GitHub ID</dt>
                <dd className="tabular-nums font-mono font-medium text-ink-primary">
                  {user?.login ? `@${user.login}` : (user?.githubId ?? "—")}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="text-ink-muted">이메일</dt>
                <dd className="truncate font-mono text-ink-primary">{user?.email || "—"}</dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="shrink-0 text-ink-muted">주요 언어</dt>
                <dd className="flex flex-wrap justify-end gap-1">
                  {user?.primaryLanguages && user.primaryLanguages.length > 0 ? (
                    user.primaryLanguages.map((lang) => (
                      <span
                        className="badge-trim rounded border border-neutral-border bg-neutral-subtle px-1.5 py-0.5 font-mono text-[11px] font-medium text-ink-primary"
                        key={lang}
                      >
                        {lang}
                      </span>
                    ))
                  ) : (
                    <span className="text-ink-muted">프로필 분석 전</span>
                  )}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="text-ink-muted">벡터 업데이트</dt>
                <dd className="tabular-nums font-mono text-ink-primary">
                  {user?.vectorUpdatedAt ? (
                    formatDate(user.vectorUpdatedAt)
                  ) : (
                    <span className="font-sans font-medium text-[#b06000]">미완료</span>
                  )}
                </dd>
              </div>
            </dl>
          </section>
        </div>
      </div>
    </ProtectedLayout>
  );
}
