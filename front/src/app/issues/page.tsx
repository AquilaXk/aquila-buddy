"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { Sparkles, Compass, Loader2 } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
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
      const profileTerms = user.topics
        .map((topic) => topic.trim().toLowerCase())
        .filter((topic) => /^[a-z0-9][a-z0-9.+#-]{1,39}$/.test(topic))
        .slice(0, 2);
      const topicQuery = profileTerms.length > 0 ? ` ${profileTerms.join(" ")}` : "";

      if (!languages || languages.length === 0) {
        throw new Error("프로필 분석이 필요합니다. 마이페이지에서 벡터 업데이트를 먼저 실행해주세요.");
      }

      setRecommendStep("GitHub에서 이슈 수집 중...");
      await Promise.all(
        languages.slice(0, 3).map((language) =>
          crawlSearchIssues(`language:${language.toLowerCase()}${topicQuery} label:"good first issue" state:open`)
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

  const hasLowDensityHistory = history.length > 0 && history.length < 3;

  return (
    <ProtectedLayout>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="mb-1 text-2xl font-bold text-dark-text">추천 이슈</h1>
          <p className="text-sm text-dark-muted">
            나의 기술 스택과 GitHub 활동 기반으로 맞춤 오픈소스 이슈를 추천합니다.
          </p>
        </div>
        <button
          className="flex w-full shrink-0 items-center justify-center gap-2 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-600 active:bg-brand-700 disabled:opacity-60 sm:w-auto"
          disabled={recommending}
          onClick={handleRecommend}
          type="button"
        >
          {recommending ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
              <span>{recommendStep ?? "분석 중..."}</span>
            </>
          ) : (
            <>
              <Sparkles className="h-4 w-4" />
              <span>이슈 추천받기</span>
            </>
          )}
        </button>
      </div>

      {error ? (
        <div className="mb-6 rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-400">
          {error}
        </div>
      ) : null}

      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((item) => (
            <div className="h-28 animate-pulse rounded-xl bg-dark-surface border border-dark-border" key={item} />
          ))}
        </div>
      ) : history.length === 0 ? (
        <EmptyIssuesState
          onRecommend={handleRecommend}
          recommendStep={recommendStep}
          recommending={recommending}
        />
      ) : (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <div className="space-y-3">
            {history.map((issue) => (
              <IssueHistoryCard issue={issue} key={issue.id} />
            ))}
          </div>
          {hasLowDensityHistory ? (
            <LowDensityIssueGuide
              onRecommend={handleRecommend}
              recommendStep={recommendStep}
              recommending={recommending}
            />
          ) : null}
        </div>
      )}
    </ProtectedLayout>
  );
}

function EmptyIssuesState({
  onRecommend,
  recommendStep,
  recommending
}: {
  onRecommend: () => void;
  recommendStep: string | null;
  recommending: boolean;
}) {
  return (
    <section className="rounded-xl border border-dashed border-dark-border bg-dark-surface/50 px-5 py-16 text-center">
      <div className="mx-auto mb-4 grid h-12 w-12 place-items-center rounded-2xl bg-brand-500/10 text-brand-400">
        <Sparkles className="h-6 w-6" />
      </div>
      <p className="text-base font-bold text-dark-text">아직 추천받은 이슈가 없습니다.</p>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-dark-muted">
        프로필 분석을 마쳤다면 바로 추천을 실행해 첫 후보를 받아보세요.
      </p>
      <button
        className="mt-6 inline-flex items-center justify-center gap-2 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-600 active:bg-brand-700 disabled:opacity-60"
        disabled={recommending}
        onClick={onRecommend}
        type="button"
      >
        {recommending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" />
            <span>{recommendStep ?? "분석 중..."}</span>
          </>
        ) : (
          <>
            <Sparkles className="h-4 w-4" />
            <span>이슈 추천받기</span>
          </>
        )}
      </button>
    </section>
  );
}

function LowDensityIssueGuide({
  onRecommend,
  recommendStep,
  recommending
}: {
  onRecommend: () => void;
  recommendStep: string | null;
  recommending: boolean;
}) {
  return (
    <aside className="rounded-xl border border-dark-border bg-dark-surface p-5 lg:sticky lg:top-24 lg:self-start">
      <p className="text-sm font-bold text-dark-text">더 넓게 비교하기</p>
      <p className="mt-2 text-sm leading-6 text-dark-muted">
        추천 후보가 적을 때는 프로필 분석을 확인하거나 추천을 다시 실행해 후보군을 넓혀보세요.
      </p>
      <div className="mt-5 grid gap-2">
        <Link
          className="focus-ring inline-flex items-center justify-center gap-1.5 rounded-xl border border-dark-border px-3 py-2 text-center text-sm font-semibold text-brand-400 hover:bg-dark-elevated transition-colors"
          href="/profile"
        >
          <Compass className="h-4 w-4" />
          <span>프로필 확인</span>
        </Link>
        <button
          className="rounded-xl border border-brand-500/50 bg-brand-500/10 px-3 py-2 text-sm font-semibold text-brand-400 transition-colors hover:bg-brand-500/20 disabled:opacity-60"
          disabled={recommending}
          onClick={onRecommend}
          type="button"
        >
          {recommending ? recommendStep ?? "분석 중..." : "추천 다시 받기"}
        </button>
      </div>
    </aside>
  );
}

function IssueHistoryCard({ issue }: { issue: RecommendedIssueHistory }) {
  const scorePercent = Math.round(issue.score * 100);
  const hasSimilarityScore = issue.score > 0.01;

  return (
    <Link
      className="block rounded-xl border border-dark-border bg-dark-surface p-5 transition-all duration-200 hover:border-brand-500/60 hover:shadow-md hover:shadow-brand-500/5 group"
      href={`/issues/${issue.id}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <StatusBadge status={issue.status} />
            <span className="text-xs font-mono text-dark-muted">
              {issue.repoFullName} #{issue.issueNumber}
            </span>
            {hasSimilarityScore ? (
              <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 text-xs font-semibold text-emerald-400">
                매칭 {scorePercent}%
              </span>
            ) : null}
            {issue.isAnalyzed ? (
              <span className="rounded-full border border-brand-500/30 bg-brand-500/10 px-2 py-0.5 text-xs font-semibold text-brand-400">
                분석 완료
              </span>
            ) : null}
          </div>
          <h3 className="mb-2 line-clamp-1 font-semibold text-dark-text group-hover:text-brand-300 transition-colors">
            {issue.title}
          </h3>
          {issue.summary ? (
            <p className="line-clamp-2 text-xs leading-5 text-dark-muted">{issue.summary}</p>
          ) : null}
          {issue.labels.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-1.5">
              {issue.labels.map((label) => (
                <span
                  className="rounded-full border border-dark-border bg-dark-elevated px-2.5 py-0.5 text-xs text-dark-muted"
                  key={label}
                >
                  {label}
                </span>
              ))}
            </div>
          ) : null}
        </div>
        <span className="mt-1 shrink-0 text-xs font-semibold text-brand-400 group-hover:translate-x-0.5 transition-transform">
          자세히 →
        </span>
      </div>
    </Link>
  );
}

function StatusBadge({ status }: { status: RecommendedIssueHistory["status"] }) {
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
        status === "OPEN"
          ? "border border-emerald-500/30 bg-emerald-500/10 text-emerald-400"
          : "border border-dark-border bg-dark-elevated text-dark-muted"
      }`}
    >
      {status === "OPEN" ? "● OPEN" : "○ CLOSED"}
    </span>
  );
}
