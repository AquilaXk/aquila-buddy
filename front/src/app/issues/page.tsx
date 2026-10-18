"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, AlertCircle, RefreshCw } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import {
  crawlSearchIssues,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  getUserProfile,
  type RecommendedIssue,
  type RecommendedIssueHistory
} from "@/lib/api";

type RecommendStep = "" | "사용자 정보 확인 중..." | "GitHub에서 이슈 수집 중..." | "나에게 맞는 이슈 분석 중...";
type AnalysisFilter = "ALL" | "ANALYZED" | "PENDING";

const analysisFilters: Array<{ value: AnalysisFilter; label: string }> = [
  { value: "ALL", label: "전체" },
  { value: "ANALYZED", label: "분석 완료" },
  { value: "PENDING", label: "분석 전" }
];

export default function IssuesPage() {
  const [history, setHistory] = useState<RecommendedIssueHistory[]>([]);
  const [selectedRepo, setSelectedRepo] = useState("ALL");
  const [analysisFilter, setAnalysisFilter] = useState<AnalysisFilter>("ALL");
  const [loading, setLoading] = useState(true);
  const [recommending, setRecommending] = useState(false);
  const [recommendStep, setRecommendStep] = useState<RecommendStep>("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    getRecommendedIssueHistory()
      .then((loadedHistory) => {
        if (!isMounted) {
          return;
        }

        setHistory(loadedHistory);
        setError(null);
      })
      .catch((caught) => {
        if (!isMounted) {
          return;
        }

        setError(caught instanceof Error ? caught.message : "추천 이력을 불러오지 못했습니다.");
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

  useEffect(() => {
    if (selectedRepo !== "ALL" && !history.some((issue) => issue.repoFullName === selectedRepo)) {
      setSelectedRepo("ALL");
    }
  }, [history, selectedRepo]);

  async function handleRecommend() {
    setRecommending(true);
    setError(null);

    try {
      setRecommendStep("사용자 정보 확인 중...");
      const profile = await getUserProfile();
      const languages = profile.primaryLanguages ?? [];

      setRecommendStep("GitHub에서 이슈 수집 중...");
      if (languages.length > 0) {
        await Promise.all(
          languages.slice(0, 3).map((language) => crawlSearchIssues(language).catch(() => []))
        );
      }

      setRecommendStep("나에게 맞는 이슈 분석 중...");
      const newIssues = await getRecommendedIssues();

      try {
        const updatedHistory = await getRecommendedIssueHistory();
        setHistory(updatedHistory.length > 0 ? updatedHistory : toHistoryEntries(newIssues));
      } catch {
        setHistory(toHistoryEntries(newIssues));
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "추천 실행 중 오류가 발생했습니다.");
    } finally {
      setRecommending(false);
      setRecommendStep("");
    }
  }

  const repositoryOptions = getRepositoryOptions(history);
  const repoFilteredHistory = selectedRepo === "ALL"
    ? history
    : history.filter((issue) => issue.repoFullName === selectedRepo);
  const filteredHistory = repoFilteredHistory.filter((issue) => {
    if (analysisFilter === "ANALYZED") {
      return Boolean(issue.isAnalyzed);
    }

    if (analysisFilter === "PENDING") {
      return !issue.isAnalyzed;
    }

    return true;
  });
  const analyzedCount = repoFilteredHistory.filter((issue) => issue.isAnalyzed).length;
  const pendingCount = repoFilteredHistory.length - analyzedCount;
  const hasActiveFilter = selectedRepo !== "ALL" || analysisFilter !== "ALL";

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-col justify-between gap-4 border-b border-neutral-border pb-5 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] text-ink-primary">추천 이슈</h1>
          <p className="mt-1 text-xs text-ink-secondary">
            나의 기술 스택과 GitHub 활동 기반으로 맞춤 오픈소스 이슈를 추천합니다.
          </p>
        </div>

        <button
          className="btn-primary focus-ring inline-flex shrink-0 items-center justify-center gap-2 px-4 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-50"
          disabled={recommending}
          onClick={handleRecommend}
          type="button"
        >
          {recommending ? (
            <>
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              <span className="button-text">{recommendStep || "추천 중..."}</span>
            </>
          ) : (
            <>
              <RefreshCw className="h-3.5 w-3.5" />
              <span className="button-text">이슈 추천받기</span>
            </>
          )}
        </button>
      </header>

      {recommending ? (
        <div className="mb-5 flex flex-wrap items-center gap-3 rounded-md border border-brand-500/20 bg-brand-50 px-4 py-2.5 text-xs">
          <RecommendStepItem
            active={recommendStep === "사용자 정보 확인 중..."}
            done={recommendStep === "GitHub에서 이슈 수집 중..." || recommendStep === "나에게 맞는 이슈 분석 중..."}
            label="1. 프로필 확인"
          />
          <span className="text-ink-muted">·</span>
          <RecommendStepItem
            active={recommendStep === "GitHub에서 이슈 수집 중..."}
            done={recommendStep === "나에게 맞는 이슈 분석 중..."}
            label="2. 이슈 수집"
          />
          <span className="text-ink-muted">·</span>
          <RecommendStepItem
            active={recommendStep === "나에게 맞는 이슈 분석 중..."}
            done={false}
            label="3. 맞춤 분석"
          />
        </div>
      ) : null}

      {error ? (
        <div className="mb-5 flex items-center gap-2 rounded-md border border-[#c5221f]/20 bg-[#fce8e6] px-4 py-3 text-sm font-medium text-[#c5221f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      ) : null}

      {loading ? (
        <div className="surface divide-y divide-neutral-border overflow-hidden">
          <div className="h-11 animate-pulse bg-neutral-subtle" />
          <div className="h-20 animate-pulse bg-white" />
          <div className="h-20 animate-pulse bg-white" />
          <div className="h-20 animate-pulse bg-white" />
        </div>
      ) : history.length === 0 ? (
        <EmptyIssuesState onRecommend={handleRecommend} recommending={recommending} />
      ) : (
        <section className="surface overflow-hidden">
          <div className="sheet-header flex flex-col gap-3 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="저장소 필터">
              <button
                className={getFilterChipClassName(selectedRepo === "ALL")}
                onClick={() => setSelectedRepo("ALL")}
                type="button"
              >
                <span className="button-text">전체</span>
                <span className="tabular-nums ml-1 font-mono text-[11px] opacity-75">{history.length}</span>
              </button>
              {repositoryOptions.map((option) => (
                <button
                  className={getFilterChipClassName(selectedRepo === option.repoFullName)}
                  key={option.repoFullName}
                  onClick={() => setSelectedRepo(option.repoFullName)}
                  type="button"
                >
                  <span className="max-w-48 truncate font-mono text-[11px]">{option.repoFullName}</span>
                  <span className="tabular-nums ml-1 font-mono text-[11px] opacity-75">{option.count}</span>
                </button>
              ))}
            </div>

            <div className="flex flex-wrap items-center justify-between gap-2 lg:justify-end">
              <div className="flex items-center gap-1" role="group" aria-label="분석 상태 필터">
                {analysisFilters.map((filter) => {
                  const count = filter.value === "ALL"
                    ? repoFilteredHistory.length
                    : filter.value === "ANALYZED"
                      ? analyzedCount
                      : pendingCount;

                  return (
                    <button
                      className={getFilterChipClassName(analysisFilter === filter.value)}
                      key={filter.value}
                      onClick={() => setAnalysisFilter(filter.value)}
                      type="button"
                    >
                      <span className="button-text">{filter.label}</span>
                      <span className="tabular-nums ml-1 font-mono text-[11px] opacity-75">{count}</span>
                    </button>
                  );
                })}
              </div>

              {hasActiveFilter ? (
                <button
                  className="focus-ring rounded px-2 py-1 text-xs font-medium text-brand-500 transition-colors hover:text-brand-600"
                  onClick={() => {
                    setSelectedRepo("ALL");
                    setAnalysisFilter("ALL");
                  }}
                  type="button"
                >
                  필터 초기화
                </button>
              ) : null}
            </div>
          </div>

          {filteredHistory.length === 0 ? (
            <div className="px-6 py-12 text-center">
              <p className="text-sm font-medium text-ink-primary">선택한 조건에 맞는 추천 이슈가 없습니다.</p>
              <p className="mt-1 text-xs text-ink-secondary">
                저장소 또는 분석 상태 필터를 초기화해 전체 추천 이력을 확인하세요.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-neutral-border">
              {filteredHistory.map((issue) => (
                <IssueHistoryCard issue={issue} key={issue.id} />
              ))}
            </div>
          )}
        </section>
      )}
    </ProtectedLayout>
  );
}

function RecommendStepItem({
  label,
  active,
  done
}: {
  label: string;
  active: boolean;
  done: boolean;
}) {
  return (
    <span
      className={
        active
          ? "font-semibold text-brand-600"
          : done
            ? "font-medium text-[#137333]"
            : "text-ink-muted"
      }
    >
      {done ? "✓ " : ""}
      {label}
    </span>
  );
}

function EmptyIssuesState({
  recommending,
  onRecommend
}: {
  recommending: boolean;
  onRecommend: () => void;
}) {
  return (
    <section className="surface px-6 py-14 text-center">
      <h2 className="text-base font-semibold text-ink-primary">아직 추천된 이슈가 없습니다</h2>
      <p className="mx-auto mt-1.5 max-w-md text-xs leading-relaxed text-ink-secondary">
        프로필 분석을 마쳤다면 바로 추천을 실행해 첫 후보를 받아보세요.
      </p>
      <div className="mt-5 flex flex-wrap items-center justify-center gap-2.5">
        <button
          className="btn-primary focus-ring inline-flex items-center gap-2 px-4 py-2 text-xs disabled:opacity-50"
          disabled={recommending}
          onClick={onRecommend}
          type="button"
        >
          <RefreshCw className="h-3.5 w-3.5" />
          <span className="button-text">이슈 추천받기</span>
        </button>
        <Link
          className="btn-secondary focus-ring inline-flex items-center px-4 py-2 text-xs"
          href="/profile"
        >
          <span className="button-text">프로필 확인</span>
        </Link>
      </div>
    </section>
  );
}

function IssueHistoryCard({ issue }: { issue: RecommendedIssueHistory }) {
  const scorePercent = Math.round(issue.score * 100);
  const visibleLabels = issue.labels.slice(0, 4);
  const hiddenLabelCount = Math.max(issue.labels.length - visibleLabels.length, 0);

  return (
    <Link
      className="focus-ring group flex flex-col justify-between gap-3 px-5 py-4 transition-colors hover:bg-neutral-subtle sm:flex-row sm:items-start"
      href={`/issues/${encodeURIComponent(issue.id)}`}
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={issue.status} />
          <span className="font-mono text-xs text-ink-muted">
            {issue.repoFullName} #{issue.issueNumber}
          </span>
          {visibleLabels.map((label) => (
            <span
              className="badge-trim rounded border border-neutral-border bg-neutral-subtle px-1.5 py-0.5 font-mono text-[11px] text-ink-secondary"
              key={label}
            >
              {label}
            </span>
          ))}
          {hiddenLabelCount > 0 ? (
            <span className="tabular-nums font-mono text-[11px] text-ink-muted">
              +{hiddenLabelCount}
            </span>
          ) : null}
        </div>

        <h2 className="mt-1.5 text-[15px] font-medium leading-snug text-ink-primary transition-colors group-hover:text-brand-500">
          {issue.title}
        </h2>

        {issue.summary ? (
          <p className="mt-1 line-clamp-1 text-xs leading-relaxed text-ink-secondary">
            {issue.summary}
          </p>
        ) : null}

        {issue.reason ? (
          <p className="mt-1.5 line-clamp-1 text-xs text-ink-muted">
            <span className="font-medium text-ink-secondary">추천 근거:</span> {issue.reason}
          </p>
        ) : null}
      </div>

      <div className="flex shrink-0 items-center gap-2 sm:flex-col sm:items-end sm:gap-1.5">
        <div className="flex items-center gap-1.5">
          {issue.score > 0 ? (
            <span className="badge-trim tabular-nums rounded border border-[#137333]/20 bg-[#e6f4ea] px-2 py-0.5 font-mono text-xs font-medium text-[#137333]">
              매칭 {scorePercent}%
            </span>
          ) : null}
          <span
            className={`badge-trim rounded border px-2 py-0.5 text-[11px] font-medium ${
              issue.isAnalyzed
                ? "border-brand-500/20 bg-brand-50 text-brand-600"
                : "border-neutral-border bg-white text-ink-muted"
            }`}
          >
            {issue.isAnalyzed ? "분석 완료" : "분석 전"}
          </span>
        </div>
        <span className="text-xs font-medium text-brand-500 transition-colors group-hover:text-brand-600">
          상세 분석
        </span>
      </div>
    </Link>
  );
}

function getRepositoryOptions(history: RecommendedIssueHistory[]) {
  const counts = new Map<string, number>();

  for (const issue of history) {
    counts.set(issue.repoFullName, (counts.get(issue.repoFullName) ?? 0) + 1);
  }

  return Array.from(counts.entries()).map(([repoFullName, count]) => ({
    repoFullName,
    count
  }));
}

function getFilterChipClassName(active: boolean) {
  return [
    "focus-ring inline-flex items-center rounded px-2.5 py-1 text-xs font-medium transition-colors",
    active
      ? "bg-[#121317] text-white"
      : "border border-neutral-border bg-white text-ink-secondary hover:bg-neutral-hover hover:text-ink-primary"
  ].join(" ");
}

function toHistoryEntries(issues: RecommendedIssue[]): RecommendedIssueHistory[] {
  return issues.map((issue) => ({
    ...issue,
    isAnalyzed: false,
    analysisResultId: null
  }));
}
