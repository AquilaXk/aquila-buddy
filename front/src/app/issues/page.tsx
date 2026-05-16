"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ProtectedLayout } from "@/components/ProtectedLayout";
import {
  crawlSearchIssues,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  getUserProfile,
  type RecommendedIssueHistory
} from "@/lib/api";

export default function IssuesPage() {
  const [history, setHistory] = useState<RecommendedIssueHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [recommending, setRecommending] = useState(false);
  const [recommendStep, setRecommendStep] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    loadHistory();
  }, []);

  async function loadHistory() {
    setLoading(true);

    try {
      const issues = await getRecommendedIssueHistory();
      setHistory(issues);
      setError(null);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "추천 이력을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  async function handleRecommend() {
    setRecommending(true);
    setError(null);
    setRecommendStep("사용자 정보 확인 중...");

    try {
      const user = await getUserProfile();
      const languages = user.primaryLanguages;

      if (!languages || languages.length === 0) {
        throw new Error("프로필 분석이 필요합니다. 마이페이지에서 벡터 업데이트를 먼저 실행해주세요.");
      }

      setRecommendStep("GitHub에서 이슈 수집 중...");
      await Promise.all(
        languages.slice(0, 3).map((language) =>
          crawlSearchIssues(`language:${language.toLowerCase()} label:"good first issue" state:open`)
        )
      );

      setRecommendStep("나에게 맞는 이슈 분석 중...");
      await getRecommendedIssues();
      setRecommendStep(null);
      await loadHistory();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "이슈 추천에 실패했습니다.");
      setRecommendStep(null);
    } finally {
      setRecommending(false);
    }
  }

  return (
    <ProtectedLayout>
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="mb-1 text-2xl font-bold text-github-text">추천 이슈</h1>
          <p className="text-sm text-github-muted">
            나의 기술 스택과 GitHub 활동 기반으로 맞춤 오픈소스 이슈를 추천합니다.
          </p>
        </div>
        <button
          className="flex shrink-0 items-center gap-2 rounded-lg bg-github-purple px-5 py-2.5 text-sm font-medium text-white transition-colors hover:bg-github-purple/90 disabled:opacity-60"
          disabled={recommending}
          onClick={handleRecommend}
          type="button"
        >
          {recommending ? (
            <>
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
              {recommendStep ?? "분석 중..."}
            </>
          ) : (
            "이슈 추천받기"
          )}
        </button>
      </div>

      {error ? (
        <div className="mb-6 rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-400">
          {error}
        </div>
      ) : null}

      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((item) => (
            <div className="h-28 animate-pulse rounded-xl bg-github-surface" key={item} />
          ))}
        </div>
      ) : history.length === 0 ? (
        <div className="py-24 text-center text-github-muted">
          <p className="mb-1">아직 추천받은 이슈가 없습니다.</p>
          <p className="mt-1 text-xs">
            프로필 분석 완료 후 "이슈 추천받기" 버튼을 눌러보세요.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {history.map((issue) => (
            <IssueHistoryCard issue={issue} key={issue.id} />
          ))}
        </div>
      )}
    </ProtectedLayout>
  );
}

function IssueHistoryCard({ issue }: { issue: RecommendedIssueHistory }) {
  const scorePercent = Math.round(issue.score * 100);

  return (
    <Link
      className="block rounded-xl border border-github-border bg-github-surface p-5 transition-colors hover:border-github-purple/50"
      href={`/issues/${issue.id}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <StatusBadge status={issue.status} />
            <span className="text-xs text-github-muted">
              {issue.repoFullName} #{issue.issueNumber}
            </span>
            {scorePercent > 0 ? (
              <span className="rounded-full border border-green-500/20 bg-green-500/10 px-2 py-0.5 text-xs text-green-400">
                매칭 {scorePercent}%
              </span>
            ) : null}
            {issue.isAnalyzed ? (
              <span className="rounded-full border border-github-accent/20 bg-github-accent/10 px-2 py-0.5 text-xs text-github-accent">
                분석 완료
              </span>
            ) : null}
          </div>
          <h3 className="mb-2 line-clamp-1 font-medium text-github-text">{issue.title}</h3>
          {issue.summary ? (
            <p className="line-clamp-2 text-xs text-github-muted">{issue.summary}</p>
          ) : null}
          {issue.labels.length > 0 ? (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {issue.labels.map((label) => (
                <span
                  className="rounded-full border border-github-accent/20 bg-github-accent/10 px-2 py-0.5 text-xs text-github-accent"
                  key={label}
                >
                  {label}
                </span>
              ))}
            </div>
          ) : null}
        </div>
        <span className="mt-1 shrink-0 text-xs text-github-muted">자세히 →</span>
      </div>
    </Link>
  );
}

function StatusBadge({ status }: { status: RecommendedIssueHistory["status"] }) {
  return (
    <span
      className={`rounded-full px-1.5 py-0.5 text-xs ${
        status === "OPEN"
          ? "bg-green-500/20 text-green-400"
          : "bg-github-muted/20 text-github-muted"
      }`}
    >
      {status === "OPEN" ? "● OPEN" : "○ CLOSED"}
    </span>
  );
}
