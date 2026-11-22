"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { AlertCircle, ArrowUpRight, CheckCircle2, CircleDot, Code2, GitPullRequest } from "@/components/icons";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import {
  getPullRequestDraftHistory,
  getRecommendedIssueHistory,
  getUserProfile,
  type PullRequestDraftHistoryPage,
  type RecommendedIssueHistory,
  type UserProfile
} from "@/lib/api";
import { formatDate } from "@/lib/format";
import { issueDetailHref, prDetailHref } from "@/lib/routes";

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
        <div className="space-y-5">
          <div className="h-16 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <div className="h-28 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
            <div className="h-28 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
            <div className="h-28 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
            <div className="h-28 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
          </div>
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="h-72 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa] lg:col-span-8" />
            <div className="h-72 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa] lg:col-span-4" />
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
  const totalDrafts = draftPage?.totalElements ?? recentDrafts.length;

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-col justify-between gap-4 border-b border-[#d1d9e0] pb-5 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-[22px] font-semibold leading-[1.3] tracking-[-0.025em] text-[#1f2328]">
            안녕하세요, {displayName}님
          </h1>
          <p className="mt-1 text-[13.5px] font-normal leading-[1.55] text-[#59636e]">
            기여 워크스페이스 개요
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Link className="btn-secondary focus-ring px-3 py-1.5 text-xs" href="/issues">
            이슈 추천
          </Link>
          <Link className="btn-primary focus-ring px-3 py-1.5 text-xs" href="/pr-draft">
            PR 초안 생성
          </Link>
        </div>
      </header>

      {error ? (
        <div className="mb-5 flex items-center gap-2 rounded-lg border border-[#ff8182]/40 bg-[#ffebe9] px-4 py-3 text-[13.5px] font-medium text-[#d1242f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>프로필 정보를 불러오지 못했습니다: {error}</span>
        </div>
      ) : null}

      {!hasVector ? (
        <div className="mb-6 flex flex-col justify-between gap-3 rounded-lg border border-[#9a6700]/25 bg-[#fff8c5] px-4 py-3 text-[#9a6700] sm:flex-row sm:items-center">
          <div className="flex items-start gap-2.5">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-[#9a6700]" />
            <div>
              <p className="text-[13px] font-semibold text-[#1f2328]">프로필 분석이 필요합니다</p>
              <p className="mt-0.5 text-[13px] text-[#59636e]">
                이슈 추천을 받으려면 먼저 내 프로필에서 추천 기준 동기화를 완료해주세요.
              </p>
            </div>
          </div>
          <Link
            className="btn-primary focus-ring inline-flex shrink-0 items-center px-3.5 py-1.5 text-xs"
            href="/profile"
          >
            <span className="button-text">프로필로 이동</span>
          </Link>
        </div>
      ) : null}

      <section aria-label="핵심 지표" className="mb-6 grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card>
          <CardHeader className="pb-1 pt-3.5">
            <span className="text-xs font-medium text-[#59636e]">추천 이슈</span>
            <Badge variant="accent">매칭 후보</Badge>
          </CardHeader>
          <CardContent className="pb-2 pt-1">
            <p className="tabular-nums text-[28px] font-semibold leading-tight tracking-[-0.03em] text-[#1f2328]">
              {issueHistory.length}
            </p>
          </CardContent>
          <CardFooter className="bg-transparent py-2.5 text-xs text-[#59636e]">
            <Link className="inline-flex items-center gap-1 font-medium text-[#0969da] hover:underline" href="/issues">
              <span>목록에서 탐색</span>
              <ArrowUpRight className="h-3.5 w-3.5" />
            </Link>
          </CardFooter>
        </Card>

        <Card>
          <CardHeader className="pb-1 pt-3.5">
            <span className="text-xs font-medium text-[#59636e]">코드 맥락 분석</span>
            <Badge variant="success">분석 완료</Badge>
          </CardHeader>
          <CardContent className="pb-2 pt-1">
            <p className="tabular-nums text-[28px] font-semibold leading-tight tracking-[-0.03em] text-[#1f2328]">
              {analyzedCount}
            </p>
          </CardContent>
          <CardFooter className="bg-transparent py-2.5 text-xs text-[#59636e]">
            <span className="inline-flex items-center gap-1">
              <Code2 className="h-3.5 w-3.5 text-[#59636e]" />
              <span>수정 가이드·의사 코드 확보</span>
            </span>
          </CardFooter>
        </Card>

        <Card>
          <CardHeader className="pb-1 pt-3.5">
            <span className="text-xs font-medium text-[#59636e]">작성된 PR 초안</span>
            <Badge variant="done">PR 초안</Badge>
          </CardHeader>
          <CardContent className="pb-2 pt-1">
            <p className="tabular-nums text-[28px] font-semibold leading-tight tracking-[-0.03em] text-[#1f2328]">
              {totalDrafts}
            </p>
          </CardContent>
          <CardFooter className="bg-transparent py-2.5 text-xs text-[#59636e]">
            <Link className="inline-flex items-center gap-1 font-medium text-[#0969da] hover:underline" href="/pr-draft">
              <GitPullRequest className="h-3.5 w-3.5" />
              <span>새 초안 만들기</span>
            </Link>
          </CardFooter>
        </Card>

        <Card>
          <CardHeader className="pb-1 pt-3.5">
            <span className="text-xs font-medium text-[#59636e]">추천 기준 동기화</span>
            <Badge variant={hasVector ? "success" : "warning"}>
              {hasVector ? "준비 완료" : "프로필 분석"}
            </Badge>
          </CardHeader>
          <CardContent className="pb-2 pt-1">
            <p className="tabular-nums text-[28px] font-semibold leading-tight tracking-[-0.03em] text-[#1f2328]">
              {hasVector ? "완료" : "미완료"}
            </p>
          </CardContent>
          <CardFooter className="bg-transparent py-2.5 text-xs text-[#59636e]">
            <span className="tabular-nums inline-flex items-center gap-1 font-mono text-[11.5px]">
              <CheckCircle2 className={`h-3.5 w-3.5 ${hasVector ? "text-[#1a7f37]" : "text-[#9a6700]"}`} />
              <span>{user?.vectorUpdatedAt ? formatDate(user.vectorUpdatedAt) : "프로필 동기화 필요"}</span>
            </span>
          </CardFooter>
        </Card>
      </section>

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <section className="primer-box overflow-hidden lg:col-span-8">
          <div className="primer-box-header flex flex-wrap items-center justify-between gap-3 px-4 py-3">
            <div className="flex items-center gap-2">
              <CircleDot className="h-4 w-4 text-[#1a7f37]" aria-hidden="true" />
              <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
                이슈 추천 작업 큐
              </h2>
              <Badge variant="counter">{issueHistory.length}</Badge>
            </div>
            <Link
              className="btn-secondary focus-ring px-2.5 py-1 text-xs"
              href="/issues"
            >
              전체 목록 및 추천 실행
            </Link>
          </div>

          {recentIssues.length > 0 ? (
            <div className="divide-y divide-[#d1d9e0]">
              {recentIssues.map((issue) => {
                const scorePercent = Math.round(issue.score * 100);
                return (
                  <Link
                    className="focus-ring group flex items-start justify-between gap-4 border-l-[3px] border-l-transparent px-4 py-3.5 transition-colors hover:border-l-[#0969da] hover:bg-[#f6f8fa]"
                    href={issueDetailHref(issue.id)}
                    key={issue.id}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <StatusBadge status={issue.status} />
                        <span className="truncate font-mono text-[12px] font-medium tabular-nums text-[#59636e]">
                          {issue.repoFullName} #{issue.issueNumber}
                        </span>
                      </div>
                      <p className="mt-1.5 truncate text-[15px] font-semibold leading-[1.4] text-[#1f2328] transition-colors group-hover:text-[#0969da]">
                        {issue.title}
                      </p>
                    </div>
                    <div className="flex shrink-0 flex-col items-end gap-1">
                      {issue.score > 0 ? (
                        <span className="badge-trim tabular-nums rounded-full border border-[#1a7f37]/30 bg-[#dafbe1] px-2 py-0.5 font-mono text-[11px] font-semibold text-[#1a7f37]">
                          {scorePercent}%
                        </span>
                      ) : null}
                      <span className="text-[11.5px] font-medium text-[#59636e]">
                        {issue.isAnalyzed ? "분석 완료" : "분석 전"}
                      </span>
                    </div>
                  </Link>
                );
              })}
            </div>
          ) : (
            <div className="px-4 py-12 text-center">
              <p className="text-[13.5px] text-[#59636e]">아직 저장된 추천 이슈가 없습니다.</p>
              <Link
                className="btn-secondary focus-ring mt-3 inline-flex items-center px-3.5 py-1.5 text-xs"
                href="/issues"
              >
                <span className="button-text">이슈 추천 시작하기</span>
              </Link>
            </div>
          )}
        </section>

        <div className="space-y-6 lg:col-span-4">
          <section className="primer-box overflow-hidden">
            <div className="primer-box-header flex items-center justify-between gap-3 px-4 py-3">
              <div className="flex items-center gap-2">
                <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
                  PR 초안 생성 이력
                </h2>
                <Badge variant="counter">{totalDrafts}</Badge>
              </div>
              <Link
                className="btn-secondary focus-ring px-2.5 py-1 text-xs"
                href="/pr-draft"
              >
                PR 초안 작성
              </Link>
            </div>

            {recentDrafts.length > 0 ? (
              <div className="divide-y divide-[#d1d9e0]">
                {recentDrafts.map((draft) => (
                  <Link
                    className="focus-ring group block border-l-[3px] border-l-transparent px-4 py-3 transition-colors hover:border-l-[#0969da] hover:bg-[#f6f8fa]"
                    href={prDetailHref(draft.id)}
                    key={draft.id}
                  >
                    <p className="truncate text-[14px] font-semibold leading-[1.4] text-[#1f2328] transition-colors group-hover:text-[#0969da]">
                      {draft.title}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center justify-between gap-2 font-mono text-[11.5px] text-[#59636e]">
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
                <p className="text-[13px] text-[#59636e]">생성된 PR 초안이 없습니다.</p>
              </div>
            )}
          </section>

          <section className="primer-box overflow-hidden">
            <div className="primer-box-header flex items-center justify-between gap-3 px-4 py-3">
              <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
                내 계정 정보
              </h2>
              <Link
                className="btn-secondary focus-ring px-2.5 py-1 text-xs"
                href="/profile"
              >
                프로필 편집
              </Link>
            </div>

            <dl className="divide-y divide-[#d1d9e0] text-[13px]">
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="text-[#59636e]">GitHub ID</dt>
                <dd className="tabular-nums font-mono text-[12px] font-medium text-[#1f2328]">
                  {user?.login ? `@${user.login}` : (user?.githubId ?? "—")}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="text-[#59636e]">이메일</dt>
                <dd className="truncate font-mono text-[12px] text-[#1f2328]">{user?.email || "—"}</dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="shrink-0 text-[#59636e]">주요 언어</dt>
                <dd className="flex flex-wrap justify-end gap-1">
                  {user?.primaryLanguages && user.primaryLanguages.length > 0 ? (
                    user.primaryLanguages.map((lang) => (
                      <Badge key={lang} variant="accent">
                        <span className="font-mono">{lang}</span>
                      </Badge>
                    ))
                  ) : (
                    <span className="text-xs text-[#59636e]">프로필 분석 전</span>
                  )}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4 px-4 py-2.5">
                <dt className="text-[#59636e]">추천 기준 동기화</dt>
                <dd className="tabular-nums font-mono text-[12px] text-[#1f2328]">
                  {user?.vectorUpdatedAt ? (
                    formatDate(user.vectorUpdatedAt)
                  ) : (
                    <span className="font-sans font-medium text-[#9a6700]">미완료</span>
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
