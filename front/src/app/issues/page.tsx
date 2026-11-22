"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  AlertCircle,
  Bookmark,
  GitCommitHorizontal,
  Loader2,
  RefreshCw,
  Search,
  Star,
  X,
} from "@/components/icons";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import { Badge } from "@/components/ui/badge";
import { Kbd } from "@/components/ui/kbd";
import {
  bookmarkIssue,
  crawlSearchIssues,
  getIssueBookmarks,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  getUserProfile,
  unbookmarkIssue,
  type RecommendedIssue,
  type RecommendedIssueHistory,
} from "@/lib/api";
import {
  filterAndAggregateIssues,
  type IssueSortMode,
} from "@/lib/bitapSearch";
import { parseIssueReasonMetadata } from "@/lib/issueMetadata";
import { issueDetailHref } from "@/lib/routes";
import { cn } from "@/lib/utils";

type RecommendStep = "" | "사용자 정보 확인 중..." | "GitHub에서 이슈 수집 중..." | "나에게 맞는 이슈 분석 중...";
type AnalysisFilter = "ALL" | "ANALYZED" | "PENDING";

const analysisFilters: Array<{ value: AnalysisFilter; label: string }> = [
  { value: "ALL", label: "전체" },
  { value: "ANALYZED", label: "분석 완료" },
  { value: "PENDING", label: "분석 전" },
];

function isEditableElement(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  if (!el) {
    return false;
  }
  return (
    el.tagName === "INPUT" ||
    el.tagName === "TEXTAREA" ||
    el.tagName === "SELECT" ||
    el.isContentEditable
  );
}

function mergeIssuesWithBookmarks(
  items: RecommendedIssueHistory[],
  bookmarks: RecommendedIssueHistory[]
): RecommendedIssueHistory[] {
  if (bookmarks.length === 0) {
    return items;
  }
  const byId = new Map<string, RecommendedIssueHistory>();
  for (let i = 0; i < items.length; i += 1) {
    byId.set(items[i].id, items[i]);
  }
  const merged = items.map((item) => ({ ...item }));
  const indexById = new Map<string, number>();
  for (let i = 0; i < merged.length; i += 1) {
    indexById.set(merged[i].id, i);
  }
  for (let i = 0; i < bookmarks.length; i += 1) {
    const bm = bookmarks[i];
    const existingIdx = indexById.get(bm.id);
    if (existingIdx !== undefined) {
      merged[existingIdx] = { ...merged[existingIdx], bookmarked: true };
    } else if (!byId.has(bm.id)) {
      merged.push({
        ...bm,
        isAnalyzed: Boolean(bm.isAnalyzed),
        analysisResultId: bm.analysisResultId ?? null,
        bookmarked: true,
      });
    }
  }
  return merged;
}

export default function IssuesPage() {
  const router = useRouter();
  const searchInputRef = useRef<HTMLInputElement>(null);
  const rowRefs = useRef<Map<string, HTMLDivElement>>(new Map());
  const [history, setHistory] = useState<RecommendedIssueHistory[]>([]);
  const [selectedRepo, setSelectedRepo] = useState("ALL");
  const [analysisFilter, setAnalysisFilter] = useState<AnalysisFilter>("ALL");
  const [bookmarkOnly, setBookmarkOnly] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [sortMode, setSortMode] = useState<IssueSortMode>("SCORE");
  const [activeIndex, setActiveIndex] = useState(-1);
  const [bookmarkPendingIds, setBookmarkPendingIds] = useState<Record<string, boolean>>({});
  const [loading, setLoading] = useState(true);
  const [recommending, setRecommending] = useState(false);
  const [recommendStep, setRecommendStep] = useState<RecommendStep>("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    Promise.allSettled([getRecommendedIssueHistory(), getIssueBookmarks()])
      .then(([historyResult, bookmarksResult]) => {
        if (!isMounted) {
          return;
        }
        if (historyResult.status === "rejected") {
          const caught = historyResult.reason;
          setError(caught instanceof Error ? caught.message : "추천 이력을 불러오지 못했습니다.");
          return;
        }

        const loadedBookmarks =
          bookmarksResult.status === "fulfilled" ? bookmarksResult.value : [];
        setHistory(mergeIssuesWithBookmarks(historyResult.value, loadedBookmarks));
        setError(null);
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

      const [updatedHistoryResult, bookmarksResult] = await Promise.allSettled([
        getRecommendedIssueHistory(),
        getIssueBookmarks(),
      ]);
      const baseHistory =
        updatedHistoryResult.status === "fulfilled" && updatedHistoryResult.value.length > 0
          ? updatedHistoryResult.value
          : toHistoryEntries(newIssues);
      const loadedBookmarks =
        bookmarksResult.status === "fulfilled" ? bookmarksResult.value : [];
      setHistory(mergeIssuesWithBookmarks(baseHistory, loadedBookmarks));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "추천 실행 중 오류가 발생했습니다.");
    } finally {
      setRecommending(false);
      setRecommendStep("");
    }
  }

  const handleToggleBookmark = useCallback(async (issue: RecommendedIssueHistory) => {
    if (bookmarkPendingIds[issue.id]) {
      return;
    }
    const nextBookmarked = !issue.bookmarked;
    setBookmarkPendingIds((prev) => ({ ...prev, [issue.id]: true }));
    setHistory((prev) =>
      prev.map((item) => (item.id === issue.id ? { ...item, bookmarked: nextBookmarked } : item))
    );
    setError(null);

    try {
      if (nextBookmarked) {
        await bookmarkIssue(issue.id);
      } else {
        await unbookmarkIssue(issue.id);
      }
    } catch (caught) {
      setHistory((prev) =>
        prev.map((item) => (item.id === issue.id ? { ...item, bookmarked: !nextBookmarked } : item))
      );
      setError(caught instanceof Error ? caught.message : "북마크 상태를 변경하지 못했습니다.");
    } finally {
      setBookmarkPendingIds((prev) => {
        const next = { ...prev };
        delete next[issue.id];
        return next;
      });
    }
  }, [bookmarkPendingIds]);

  const bookmarkCount = useMemo(
    () => history.reduce((count, issue) => count + (issue.bookmarked ? 1 : 0), 0),
    [history]
  );

  const aggregated = useMemo(
    () => filterAndAggregateIssues(history, selectedRepo, analysisFilter, searchQuery, sortMode),
    [history, selectedRepo, analysisFilter, searchQuery, sortMode]
  );

  const repositoryOptions = aggregated.repositoryOptions;
  const filteredHistory = useMemo(() => {
    if (!bookmarkOnly) {
      return aggregated.filtered;
    }
    return aggregated.filtered.filter((issue) => Boolean(issue.bookmarked));
  }, [aggregated.filtered, bookmarkOnly]);

  const analyzedCount = aggregated.analyzedCount;
  const pendingCount = aggregated.pendingCount;
  const hasActiveFilter =
    selectedRepo !== "ALL" ||
    analysisFilter !== "ALL" ||
    bookmarkOnly ||
    searchQuery.trim().length > 0;

  useEffect(() => {
    if (filteredHistory.length === 0) {
      setActiveIndex(-1);
      return;
    }
    if (activeIndex >= filteredHistory.length) {
      setActiveIndex(filteredHistory.length - 1);
    }
  }, [activeIndex, filteredHistory.length]);

  useEffect(() => {
    function handleKeyboardNavigation(event: KeyboardEvent) {
      if (event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      if (isEditableElement(event.target)) {
        return;
      }
      if (typeof document !== "undefined" && document.querySelector("dialog[open]")) {
        return;
      }

      if (event.key === "/") {
        event.preventDefault();
        searchInputRef.current?.focus();
        return;
      }

      if (filteredHistory.length === 0) {
        return;
      }

      if (event.key === "j") {
        event.preventDefault();
        setActiveIndex((prev) => {
          const next = prev < filteredHistory.length - 1 ? prev + 1 : 0;
          const nextId = filteredHistory[next]?.id;
          if (nextId) {
            rowRefs.current.get(nextId)?.scrollIntoView({ block: "nearest" });
          }
          return next;
        });
        return;
      }

      if (event.key === "k") {
        event.preventDefault();
        setActiveIndex((prev) => {
          const next = prev > 0 ? prev - 1 : filteredHistory.length - 1;
          const nextId = filteredHistory[next]?.id;
          if (nextId) {
            rowRefs.current.get(nextId)?.scrollIntoView({ block: "nearest" });
          }
          return next;
        });
        return;
      }

      const currentIndex = activeIndex >= 0 ? activeIndex : 0;
      const targetIssue = filteredHistory[currentIndex];
      if (!targetIssue) {
        return;
      }

      if (event.key === "o" || (event.key === "Enter" && activeIndex >= 0)) {
        const activeEl = document.activeElement as HTMLElement | null;
        if (activeEl && (activeEl.tagName === "BUTTON" || activeEl.tagName === "A")) {
          return;
        }
        event.preventDefault();
        router.push(issueDetailHref(targetIssue.id));
        return;
      }

      if (event.key === "b" && activeIndex >= 0) {
        event.preventDefault();
        void handleToggleBookmark(targetIssue);
      }
    }

    window.addEventListener("keydown", handleKeyboardNavigation);
    return () => window.removeEventListener("keydown", handleKeyboardNavigation);
  }, [activeIndex, filteredHistory, handleToggleBookmark, router]);

  return (
    <ProtectedLayout>
      <header className="mb-6 flex flex-col justify-between gap-4 border-b border-[#d1d9e0] pb-5 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-[22px] font-semibold leading-[1.3] tracking-[-0.025em] text-[#1f2328]">
            추천 이슈
          </h1>
          <p className="mt-1 text-[13.5px] font-normal leading-[1.55] text-[#59636e]">
            나의 기술 스택과 GitHub 활동 기반으로 맞춤 오픈소스 이슈를 추천합니다.
          </p>
        </div>

        <button
          className="btn-primary focus-ring inline-flex shrink-0 items-center justify-center gap-2 px-4 py-2 text-[13px] disabled:cursor-not-allowed disabled:opacity-50"
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
        <div className="mb-5 flex flex-wrap items-center gap-3 rounded-lg border border-[#0969da]/25 bg-[#ddf4ff] px-4 py-2.5 text-xs">
          <RecommendStepItem
            active={recommendStep === "사용자 정보 확인 중..."}
            done={recommendStep === "GitHub에서 이슈 수집 중..." || recommendStep === "나에게 맞는 이슈 분석 중..."}
            label="1. 프로필 확인"
          />
          <span className="text-[#59636e]">·</span>
          <RecommendStepItem
            active={recommendStep === "GitHub에서 이슈 수집 중..."}
            done={recommendStep === "나에게 맞는 이슈 분석 중..."}
            label="2. 이슈 수집"
          />
          <span className="text-[#59636e]">·</span>
          <RecommendStepItem
            active={recommendStep === "나에게 맞는 이슈 분석 중..."}
            done={false}
            label="3. 맞춤 분석"
          />
        </div>
      ) : null}

      {error ? (
        <div className="mb-5 flex items-center gap-2 rounded-lg border border-[#ff8182]/40 bg-[#ffebe9] px-4 py-3 text-[13.5px] font-medium text-[#d1242f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      ) : null}

      {loading ? (
        <div className="primer-box divide-y divide-[#d1d9e0] overflow-hidden">
          <div className="h-12 animate-pulse bg-[#f6f8fa]" />
          <div className="h-24 animate-pulse bg-white" />
          <div className="h-24 animate-pulse bg-white" />
          <div className="h-24 animate-pulse bg-white" />
        </div>
      ) : history.length === 0 ? (
        <EmptyIssuesState onRecommend={handleRecommend} recommending={recommending} />
      ) : (
        <section className="primer-box overflow-hidden">
          <div className="primer-subnav space-y-2.5 px-4 py-3">
            <div className="flex flex-wrap items-center justify-between gap-2.5">
              <div className="flex flex-wrap items-center gap-1" role="group" aria-label="분석 상태 필터">
                {analysisFilters.map((filter) => {
                  const count = filter.value === "ALL"
                    ? aggregated.repoScopedTotal
                    : filter.value === "ANALYZED"
                      ? analyzedCount
                      : pendingCount;
                  const active = analysisFilter === filter.value && !bookmarkOnly;

                  return (
                    <button
                      className={cn(
                        "focus-ring inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs transition-colors",
                        active
                          ? "border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
                          : "border-transparent font-medium text-[#59636e] hover:bg-[#eaeef2] hover:text-[#1f2328]"
                      )}
                      key={filter.value}
                      onClick={() => {
                        setAnalysisFilter(filter.value);
                        if (filter.value === "ALL") {
                          setBookmarkOnly(false);
                        }
                      }}
                      type="button"
                    >
                      <span className="button-text">{filter.label}</span>
                      <Badge variant="counter">{count}</Badge>
                    </button>
                  );
                })}

                <button
                  aria-pressed={bookmarkOnly}
                  className={cn(
                    "focus-ring inline-flex items-center gap-1.5 rounded-md border px-2.5 py-1 text-xs transition-colors",
                    bookmarkOnly
                      ? "border-[#0969da]/30 bg-[#ddf4ff] font-semibold text-[#0969da]"
                      : "border-transparent font-medium text-[#59636e] hover:bg-[#eaeef2] hover:text-[#1f2328]"
                  )}
                  onClick={() => setBookmarkOnly((prev) => !prev)}
                  type="button"
                >
                  <Bookmark className={cn("h-3 w-3", bookmarkOnly && "fill-[#0969da]")} />
                  <span className="button-text">북마크한 이슈</span>
                  <Badge variant="counter">{bookmarkCount}</Badge>
                </button>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <div className="relative flex items-center">
                  <Search className="pointer-events-none absolute left-2.5 h-3.5 w-3.5 text-[#59636e]" />
                  <input
                    aria-label="추천 이슈 필터"
                    className="focus-ring w-52 rounded-md border border-[#d1d9e0] bg-white py-1.5 pl-8 pr-8 text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da] sm:w-60"
                    onChange={(event) => setSearchQuery(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Escape" && searchQuery) {
                        event.preventDefault();
                        setSearchQuery("");
                      } else if (event.key === "Enter" && (event.nativeEvent.isComposing || event.keyCode === 229)) {
                        return;
                      }
                    }}
                    placeholder="제목·라벨·#번호 검색"
                    ref={searchInputRef}
                    type="text"
                    value={searchQuery}
                  />
                  {searchQuery ? (
                    <button
                      aria-label="검색어 지우기"
                      className="focus-ring absolute right-2 rounded p-0.5 text-[#59636e] hover:text-[#1f2328]"
                      onClick={() => setSearchQuery("")}
                      type="button"
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  ) : (
                    <Kbd className="absolute right-2">/</Kbd>
                  )}
                </div>

                <select
                  aria-label="이슈 정렬 기준"
                  className="focus-ring rounded-md border border-[#d1d9e0] bg-white px-2.5 py-1.5 text-xs font-medium text-[#1f2328] shadow-xs"
                  onChange={(event) => setSortMode(event.target.value as IssueSortMode)}
                  value={sortMode}
                >
                  <option value="SCORE">매칭순</option>
                  <option value="ISSUE_ASC">번호순</option>
                </select>

                {hasActiveFilter ? (
                  <button
                    className="focus-ring rounded-md px-2 py-1 text-xs font-medium text-[#0969da] transition-colors hover:bg-[#ddf4ff]"
                    onClick={() => {
                      setSelectedRepo("ALL");
                      setAnalysisFilter("ALL");
                      setBookmarkOnly(false);
                      setSearchQuery("");
                    }}
                    type="button"
                  >
                    필터 초기화
                  </button>
                ) : null}
              </div>
            </div>

            {repositoryOptions.length > 0 ? (
              <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="저장소 필터">
                <button
                  className={getRepoPillClassName(selectedRepo === "ALL")}
                  onClick={() => setSelectedRepo("ALL")}
                  type="button"
                >
                  <span>전체 저장소</span>
                  <span className="tabular-nums ml-1 font-mono text-[11px] opacity-80">{history.length}</span>
                </button>
                {repositoryOptions.map((option) => (
                  <button
                    className={getRepoPillClassName(selectedRepo === option.repoFullName)}
                    key={option.repoFullName}
                    onClick={() => setSelectedRepo(option.repoFullName)}
                    type="button"
                  >
                    <span className="max-w-48 truncate font-mono text-[11.5px]">{option.repoFullName}</span>
                    <span className="tabular-nums ml-1 font-mono text-[11px] opacity-80">{option.count}</span>
                  </button>
                ))}
              </div>
            ) : null}
          </div>

          {filteredHistory.length === 0 ? (
            <div className="px-6 py-12 text-center">
              <p className="text-[14px] font-semibold text-[#1f2328]">선택한 조건에 맞는 추천 이슈가 없습니다.</p>
              <p className="mt-1 text-[13px] text-[#59636e]">
                저장소, 분석 상태, 북마크 또는 검색 필터를 초기화해 전체 추천 이력을 확인하세요.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-[#d1d9e0]">
              {filteredHistory.map((issue, index) => (
                <IssueHistoryCard
                  active={index === activeIndex}
                  bookmarkPending={Boolean(bookmarkPendingIds[issue.id])}
                  deferred={index >= 8}
                  issue={issue}
                  key={issue.id}
                  onFocusRow={() => setActiveIndex(index)}
                  onRegisterRow={(el) => {
                    if (el) {
                      rowRefs.current.set(issue.id, el);
                    } else {
                      rowRefs.current.delete(issue.id);
                    }
                  }}
                  onToggleBookmark={() => void handleToggleBookmark(issue)}
                />
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
          ? "font-semibold text-[#0969da]"
          : done
            ? "font-medium text-[#1a7f37]"
            : "text-[#59636e]"
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
    <section className="primer-box px-6 py-14 text-center">
      <h2 className="text-[16px] font-semibold text-[#1f2328]">아직 추천된 이슈가 없습니다</h2>
      <p className="mx-auto mt-1.5 max-w-md text-[13.5px] leading-[1.55] text-[#59636e]">
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

function IssueHistoryCard({
  issue,
  active = false,
  deferred = false,
  bookmarkPending = false,
  onFocusRow,
  onRegisterRow,
  onToggleBookmark,
}: {
  issue: RecommendedIssueHistory;
  active?: boolean;
  deferred?: boolean;
  bookmarkPending?: boolean;
  onFocusRow: () => void;
  onRegisterRow: (el: HTMLDivElement | null) => void;
  onToggleBookmark: () => void;
}) {
  const scorePercent = Math.round(issue.score * 100);
  const visibleLabels = issue.labels.slice(0, 4);
  const hiddenLabelCount = Math.max(issue.labels.length - visibleLabels.length, 0);
  const meta = useMemo(() => parseIssueReasonMetadata(issue.reason), [issue.reason]);

  return (
    <div
      className={cn(
        "group flex flex-col justify-between gap-3 border-l-[3px] px-5 py-4 transition-colors sm:flex-row sm:items-start",
        active
          ? "border-l-[#0969da] bg-[#f0f6fc]"
          : "border-l-transparent hover:bg-[#f6f8fa]",
        deferred && "studio-deferred-block"
      )}
      data-active={active ? "true" : "false"}
      onMouseEnter={onFocusRow}
      ref={onRegisterRow}
    >
      <Link
        className="focus-ring min-w-0 flex-1 rounded-md"
        href={issueDetailHref(issue.id)}
      >
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={issue.status} />
          <span className="font-mono text-[12px] font-medium tabular-nums text-[#59636e]">
            {issue.repoFullName} #{issue.issueNumber}
          </span>
          {meta.starsLabel ? (
            <span className="badge-trim tabular-nums inline-flex items-center gap-1 rounded-full border border-[#9a6700]/25 bg-[#fff8c5] px-2 py-0.5 font-mono text-[11px] font-medium text-[#9a6700]">
              <Star className="h-3 w-3" aria-hidden="true" />
              <span>{meta.starsLabel}</span>
            </span>
          ) : null}
          {meta.recentCommitLabel ? (
            <span className="badge-trim tabular-nums inline-flex items-center gap-1 rounded-full border border-[#d1d9e0] bg-[#f6f8fa] px-2 py-0.5 font-mono text-[11px] text-[#59636e]">
              <GitCommitHorizontal className="h-3 w-3" aria-hidden="true" />
              <span>{meta.recentCommitLabel}</span>
            </span>
          ) : null}
          {visibleLabels.map((label) => (
            <span
              className="badge-trim rounded-full border border-[#d1d9e0] bg-[#f6f8fa] px-2 py-0.5 font-mono text-[11px] font-medium text-[#59636e]"
              key={label}
            >
              {label}
            </span>
          ))}
          {hiddenLabelCount > 0 ? (
            <span className="tabular-nums font-mono text-[11px] text-[#59636e]">
              +{hiddenLabelCount}
            </span>
          ) : null}
        </div>

        <h2 className="mt-1.5 text-[15px] font-semibold leading-[1.4] text-[#1f2328] transition-colors group-hover:text-[#0969da]">
          {issue.title}
        </h2>

        {issue.summary ? (
          <p className="mt-1 line-clamp-1 text-[13.5px] font-normal leading-[1.55] text-[#59636e]">
            {issue.summary}
          </p>
        ) : null}

        {meta.cleanReason ? (
          <p className="mt-2 line-clamp-1 rounded-r-md border-l-2 border-[#0969da] bg-[#f6f8fa] px-2.5 py-1 text-[12.5px] text-[#59636e]">
            <span className="font-semibold text-[#1f2328]">추천 근거:</span> {meta.cleanReason}
          </p>
        ) : null}
      </Link>

      <div className="flex shrink-0 items-center gap-2 sm:flex-col sm:items-end sm:gap-1.5">
        <div className="flex items-center gap-1.5">
          <button
            aria-label={issue.bookmarked ? "북마크 해제" : "북마크 추가"}
            aria-pressed={Boolean(issue.bookmarked)}
            className={cn(
              "focus-ring inline-flex h-7 w-7 items-center justify-center rounded-md border transition-colors disabled:opacity-50",
              issue.bookmarked
                ? "border-[#0969da]/30 bg-[#ddf4ff] text-[#0969da]"
                : "border-[#d1d9e0] bg-white text-[#59636e] hover:border-[#b9c3ce] hover:text-[#1f2328]"
            )}
            disabled={bookmarkPending}
            onClick={onToggleBookmark}
            title={issue.bookmarked ? "북마크 해제 (b)" : "북마크 추가 (b)"}
            type="button"
          >
            <Bookmark className={cn("h-3.5 w-3.5", issue.bookmarked && "fill-[#0969da]")} />
          </button>
          {issue.score > 0 ? (
            <span className="badge-trim tabular-nums rounded-full border border-[#1a7f37]/30 bg-[#dafbe1] px-2.5 py-0.5 font-mono text-xs font-semibold text-[#1a7f37]">
              매칭 {scorePercent}%
            </span>
          ) : null}
          <span
            className={cn(
              "badge-trim rounded-full border px-2 py-0.5 text-[11px] font-medium",
              issue.isAnalyzed
                ? "border-[#0969da]/25 bg-[#ddf4ff] text-[#0969da]"
                : "border-[#d1d9e0] bg-[#f6f8fa] text-[#59636e]"
            )}
          >
            {issue.isAnalyzed ? "분석 완료" : "분석 전"}
          </span>
        </div>
        <Link
          className="focus-ring text-xs font-medium text-[#0969da] transition-colors group-hover:underline"
          href={issueDetailHref(issue.id)}
        >
          상세 분석
        </Link>
      </div>
    </div>
  );
}

function getRepoPillClassName(active: boolean) {
  return cn(
    "focus-ring inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs transition-colors",
    active
      ? "border-[#0969da]/30 bg-[#ddf4ff] font-semibold text-[#0969da]"
      : "border-[#d1d9e0] bg-white font-medium text-[#59636e] hover:border-[#b9c3ce] hover:text-[#1f2328]"
  );
}

function toHistoryEntries(issues: RecommendedIssue[]): RecommendedIssueHistory[] {
  return issues.map((issue) => ({
    ...issue,
    isAnalyzed: false,
    analysisResultId: null
  }));
}
