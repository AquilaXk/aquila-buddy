"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  getIssueGuide,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  type IssueGuide,
  type RecommendedIssue,
  type RecommendedIssueHistory
} from "@/lib/api";

type IssueDetailIssue = RecommendedIssue | RecommendedIssueHistory;

export default function IssueDetailPage() {
  const params = useParams<{ id: string }>();
  const issueId = params.id;
  const [issue, setIssue] = useState<IssueDetailIssue | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [guide, setGuide] = useState<IssueGuide | null>(null);
  const [guideLoading, setGuideLoading] = useState(false);
  const [guideError, setGuideError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadIssue() {
      setLoading(true);
      setNotFound(false);

      const [historyResult, recommendationsResult] = await Promise.allSettled([
        getRecommendedIssueHistory(),
        getRecommendedIssues()
      ]);
      const history = historyResult.status === "fulfilled" ? historyResult.value : [];
      const recommendations = recommendationsResult.status === "fulfilled" ? recommendationsResult.value : [];
      const foundIssue = [...history, ...recommendations].find((item) => item.id === issueId) ?? null;

      if (!isMounted) {
        return;
      }

      setIssue(foundIssue);
      setNotFound(!foundIssue);
      setGuide(null);
      setGuideError(null);
      setLoading(false);
    }

    loadIssue().catch(() => {
      if (!isMounted) {
        return;
      }

      setIssue(null);
      setNotFound(true);
      setLoading(false);
    });

    return () => {
      isMounted = false;
    };
  }, [issueId]);

  async function handleGetGuide() {
    setGuideLoading(true);
    setGuideError(null);

    try {
      setGuide(await getIssueGuide(issueId));
    } catch (caught) {
      setGuideError(caught instanceof Error ? caught.message : "가이드를 불러오지 못했습니다.");
    } finally {
      setGuideLoading(false);
    }
  }

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="surface h-24 animate-pulse" />
          <div className="surface h-32 animate-pulse" />
          <div className="surface h-32 animate-pulse" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !issue) {
    return (
      <ProtectedLayout>
        <section className="surface py-20 text-center">
          <h1 className="text-2xl font-bold text-github-text">이슈를 찾을 수 없습니다.</h1>
          <p className="mt-3 text-sm font-semibold text-github-muted">
            추천 이력에서 선택한 이슈 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-6 inline-flex text-sm font-semibold text-github-accent"
            href="/issues"
          >
            ← 추천 이슈 목록으로
          </Link>
        </section>
      </ProtectedLayout>
    );
  }

  const githubUrl = `https://github.com/${issue.repoFullName}/issues/${issue.issueNumber}`;
  const scorePercent = Math.round(issue.score * 100);

  return (
    <ProtectedLayout>
      <div className="mb-6">
        <Link
          className="focus-ring text-sm font-semibold text-github-muted hover:text-github-text"
          href="/issues"
        >
          ← 추천 이슈 목록
        </Link>
      </div>

      <section className="surface mb-5 p-6">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <StatusBadge status={issue.status} />
          <a
            className="text-xs font-semibold text-github-muted hover:text-github-accent"
            href={githubUrl}
            rel="noopener noreferrer"
            target="_blank"
          >
            {issue.repoFullName} #{issue.issueNumber} ↗
          </a>
          {issue.score > 0 ? (
            <span className="rounded-md border border-green-500/20 bg-green-500/10 px-2 py-1 text-xs font-semibold text-green-400">
              매칭 {scorePercent}%
            </span>
          ) : null}
          {"isAnalyzed" in issue ? (
            <span className="rounded-md border border-github-accent/20 bg-github-accent/10 px-2 py-1 text-xs font-semibold text-github-accent">
              {getIssueAnalysisLabel(issue)}
            </span>
          ) : null}
        </div>

        <h1 className="text-xl font-bold leading-7 text-github-text">{issue.title}</h1>

        {issue.labels.length > 0 ? (
          <div className="mt-4 flex flex-wrap gap-2">
            {issue.labels.map((label) => (
              <span
                className="rounded-md border border-github-accent/20 bg-github-accent/10 px-2 py-1 text-xs font-semibold text-github-accent"
                key={label}
              >
                {label}
              </span>
            ))}
          </div>
        ) : null}

        {issue.summary ? (
          <div className="mt-5 rounded-md bg-github-bg p-4">
            <p className="text-xs font-semibold text-github-muted">추천 이유</p>
            <p className="mt-2 text-sm leading-6 text-github-text">{issue.summary}</p>
          </div>
        ) : null}
      </section>

      <section className="surface mb-4 p-6">
        <div className="mb-3 flex items-start justify-between gap-4">
          <div>
            <h2 className="font-semibold text-github-text">1차 힌트 — 수정 가이드</h2>
            <p className="mt-1 text-xs leading-5 text-github-muted">
              이슈 해결을 시작하기 전에 수정 방향과 주의 사항을 확인합니다.
            </p>
          </div>
          {!guide ? (
            <button
              className="focus-ring shrink-0 rounded-md bg-github-purple/20 px-4 py-2 text-sm font-semibold text-github-purple-light hover:bg-github-purple/30 disabled:cursor-not-allowed disabled:opacity-50"
              disabled={guideLoading}
              onClick={handleGetGuide}
              type="button"
            >
              {guideLoading ? "분석 중..." : "힌트 보기"}
            </button>
          ) : null}
        </div>

        {guideError ? (
          <p className="text-sm font-semibold text-red-400">{guideError}</p>
        ) : null}

        {guide ? (
          <div className="space-y-4">
            <div>
              <p className="mb-2 text-xs font-semibold text-github-muted">가이드라인</p>
              <p className="whitespace-pre-wrap text-sm leading-6 text-github-text">
                {guide.guideline}
              </p>
            </div>
            {guide.sideEffects.length > 0 ? (
              <div>
                <p className="mb-2 text-xs font-semibold text-github-muted">주의 사항</p>
                <ul className="list-disc space-y-2 pl-4 text-sm leading-6 text-github-text">
                  {guide.sideEffects.map((sideEffect) => (
                    <li key={sideEffect}>{sideEffect}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : null}
      </section>
    </ProtectedLayout>
  );
}

function StatusBadge({ status }: { status: RecommendedIssue["status"] }) {
  return (
    <span
      className={`rounded-md px-2 py-1 text-xs font-semibold ${
        status === "OPEN"
          ? "border border-green-500/20 bg-green-500/10 text-green-400"
          : "border border-github-border bg-github-bg text-github-muted"
      }`}
    >
      {status === "OPEN" ? "● OPEN" : "○ CLOSED"}
    </span>
  );
}

function getIssueAnalysisLabel(issue: RecommendedIssueHistory) {
  return issue.isAnalyzed ? "분석 완료" : "분석 전";
}
