"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { Sparkles, Compass, Loader2, Filter } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import {
  crawlSearchIssues,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  getUserProfile,
  type RecommendedIssueHistory
} from "@/lib/api";

const LANGUAGE_REGEX_MAP: Record<string, RegExp> = {
  "c++": /(?:^|[^a-zA-Z0-9])(?:c\+\+|cpp)(?:$|[^a-zA-Z0-9])/i,
  cpp: /(?:^|[^a-zA-Z0-9])(?:c\+\+|cpp)(?:$|[^a-zA-Z0-9])/i,
  "c#": /(?:^|[^a-zA-Z0-9])(?:c#|csharp)(?:$|[^a-zA-Z0-9])/i,
  csharp: /(?:^|[^a-zA-Z0-9])(?:c#|csharp)(?:$|[^a-zA-Z0-9])/i,
  go: /\b(?:go|golang)\b/i,
  golang: /\b(?:go|golang)\b/i,
  java: /\bjava\b(?!script)/i,
  javascript: /\b(?:javascript|js)\b/i,
  js: /\b(?:javascript|js)\b/i,
  typescript: /\b(?:typescript|ts)\b/i,
  ts: /\b(?:typescript|ts)\b/i,
  python: /\b(?:python|py)\b/i,
  py: /\b(?:python|py)\b/i,
  rust: /\brust\b/i,
  kotlin: /\bkotlin\b/i
};

function matchesLanguageText(text: string, key: string): boolean {
  if (!text || !key) return false;
  const normalizedKey = key.toLowerCase();
  const pattern = LANGUAGE_REGEX_MAP[normalizedKey];
  if (pattern) {
    return pattern.test(text);
  }
  return text.toLowerCase().includes(normalizedKey);
}

export default function IssuesPage() {
  const [history, setHistory] = useState<RecommendedIssueHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [recommending, setRecommending] = useState(false);
  const [recommendStep, setRecommendStep] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selectedLanguage, setSelectedLanguage] = useState<string>("ALL");
  const [selectedLabel, setSelectedLabel] = useState<string>("ALL");

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

  const availableLanguages = useMemo(() => {
    const langs = new Set<string>();
    const knownLangs = [
      { key: "java", label: "Java" },
      { key: "typescript", label: "TypeScript" },
      { key: "javascript", label: "JavaScript" },
      { key: "python", label: "Python" },
      { key: "go", label: "Go" },
      { key: "rust", label: "Rust" },
      { key: "c++", label: "C++" },
      { key: "kotlin", label: "Kotlin" }
    ];
    history.forEach((issue) => {
      const labels = Array.isArray(issue.labels) ? issue.labels.filter(Boolean) : [];
      const text = `${issue.repoFullName ?? ""} ${issue.title ?? ""} ${labels.join(" ")} ${issue.reason ?? ""}`;
      knownLangs.forEach(({ key, label }) => {
        if (matchesLanguageText(text, key)) {
          langs.add(label);
        }
      });
    });
    return Array.from(langs).sort((a, b) => a.localeCompare(b));
  }, [history]);

  const availableLabels = useMemo(() => {
    const counts = new Map<string, number>();
    history.forEach((issue) => {
      if (Array.isArray(issue.labels)) {
        issue.labels.filter(Boolean).forEach((label) => {
          counts.set(label, (counts.get(label) ?? 0) + 1);
        });
      }
    });
    return Array.from(counts.entries())
      .sort((a, b) => b[1] - a[1])
      .map(([name]) => name)
      .slice(0, 6);
  }, [history]);

  const filteredHistory = useMemo(() => {
    return history.filter((issue) => {
      const labels = Array.isArray(issue.labels) ? issue.labels.filter(Boolean) : [];
      if (selectedLanguage !== "ALL") {
        const text = `${issue.repoFullName ?? ""} ${issue.title ?? ""} ${labels.join(" ")} ${issue.reason ?? ""}`;
        if (!matchesLanguageText(text, selectedLanguage)) {
          return false;
        }
      }
      if (selectedLabel !== "ALL") {
        if (!labels.includes(selectedLabel)) {
          return false;
        }
      }
      return true;
    });
  }, [history, selectedLanguage, selectedLabel]);

  const hasLowDensityHistory = history.length > 0 && history.length < 3;

  return (
    <ProtectedLayout>
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h1 className="mb-1 text-2xl font-medium text-dark-text">추천 이슈</h1>
          <p className="text-sm text-dark-muted">
            나의 기술 스택과 GitHub 활동 기반으로 맞춤 오픈소스 이슈를 추천합니다.
          </p>
        </div>
        <button
          className="btn-primary focus-ring flex w-full shrink-0 items-center justify-center gap-2 px-5 py-2.5 text-sm disabled:opacity-60 sm:w-auto"
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
        <div className="mb-6 rounded-lg border border-[#c5221f]/20 bg-[#fce8e6] p-4 text-sm font-medium text-[#c5221f]">
          {error}
        </div>
      ) : null}

      {loading ? (
        <div className="space-y-3">
          {[0, 1, 2, 3].map((item) => (
            <div className="surface h-28 animate-pulse bg-dark-elevated" key={item} />
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
            <div className="surface p-4">
              <div className="flex flex-col gap-3">
                <div className="flex flex-wrap items-center gap-1.5">
                  <span className="flex items-center gap-1 text-xs font-medium text-dark-muted">
                    <Filter className="h-3.5 w-3.5" />
                    언어:
                  </span>
                  <button
                    className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                      selectedLanguage === "ALL"
                        ? "border border-[#121317] bg-[#121317] text-white"
                        : "border border-dark-border bg-dark-elevated text-dark-muted hover:text-dark-text"
                    }`}
                    onClick={() => setSelectedLanguage("ALL")}
                    type="button"
                  >
                    전체
                  </button>
                  {availableLanguages.map((lang) => (
                    <button
                      className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                        selectedLanguage === lang
                          ? "border border-[#121317] bg-[#121317] text-white"
                          : "border border-dark-border bg-dark-elevated text-dark-muted hover:text-dark-text"
                      }`}
                      key={lang}
                      onClick={() => setSelectedLanguage(selectedLanguage === lang ? "ALL" : lang)}
                      type="button"
                    >
                      {lang}
                    </button>
                  ))}
                </div>
                {availableLabels.length > 0 ? (
                  <div className="flex flex-wrap items-center gap-1.5 border-t border-dark-border pt-2">
                    <span className="text-xs font-medium text-dark-muted">라벨:</span>
                    <button
                      className={`rounded-md px-2 py-0.5 text-xs font-medium transition-colors ${
                        selectedLabel === "ALL"
                          ? "border border-[#121317] bg-[#121317] text-white"
                          : "border border-dark-border bg-dark-elevated text-dark-muted hover:text-dark-text"
                      }`}
                      onClick={() => setSelectedLabel("ALL")}
                      type="button"
                    >
                      전체
                    </button>
                    {availableLabels.map((lbl) => (
                      <button
                        className={`rounded-md px-2 py-0.5 text-xs font-medium transition-colors ${
                          selectedLabel === lbl
                            ? "border border-[#121317] bg-[#121317] text-white"
                            : "border border-dark-border bg-dark-elevated text-dark-muted hover:text-dark-text"
                        }`}
                        key={lbl}
                        onClick={() => setSelectedLabel(selectedLabel === lbl ? "ALL" : lbl)}
                        type="button"
                      >
                        {lbl}
                      </button>
                    ))}
                  </div>
                ) : null}
              </div>
            </div>

            {filteredHistory.length === 0 ? (
              <div className="rounded-lg border border-dashed border-dark-border bg-dark-elevated p-8 text-center">
                <p className="text-sm font-medium text-dark-muted">선택한 필터 조건에 일치하는 추천 이슈가 없습니다.</p>
                <button
                  className="mt-3 text-xs font-medium text-brand-500 underline hover:text-brand-600"
                  onClick={() => {
                    setSelectedLanguage("ALL");
                    setSelectedLabel("ALL");
                  }}
                  type="button"
                >
                  필터 초기화
                </button>
              </div>
            ) : (
              filteredHistory.map((issue) => (
                <IssueHistoryCard issue={issue} key={issue.id} />
              ))
            )}
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
    <section className="rounded-lg border border-dashed border-dark-border bg-dark-elevated px-5 py-16 text-center">
      <div className="mx-auto mb-4 grid h-11 w-11 place-items-center rounded-xl border border-dark-border bg-white text-dark-text">
        <Sparkles className="h-5 w-5" />
      </div>
      <p className="text-base font-medium text-dark-text">아직 추천받은 이슈가 없습니다.</p>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-dark-muted">
        프로필 분석을 마쳤다면 바로 추천을 실행해 첫 후보를 받아보세요.
      </p>
      <button
        className="btn-primary focus-ring mt-6 inline-flex items-center justify-center gap-2 px-5 py-2.5 text-sm disabled:opacity-60"
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
    <aside className="surface p-5 lg:sticky lg:top-24 lg:self-start">
      <p className="text-sm font-medium text-dark-text">더 넓게 비교하기</p>
      <p className="mt-2 text-xs leading-5 text-dark-muted">
        추천 후보가 적을 때는 프로필 분석을 확인하거나 추천을 다시 실행해 후보군을 넓혀보세요.
      </p>
      <div className="mt-4 grid gap-2">
        <Link
          className="btn-secondary focus-ring inline-flex items-center justify-center gap-1.5 px-3 py-2 text-center text-xs"
          href="/profile"
        >
          <Compass className="h-3.5 w-3.5" />
          <span>프로필 확인</span>
        </Link>
        <button
          className="btn-primary focus-ring px-3 py-2 text-xs disabled:opacity-60"
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
  const uniqueLabels = Array.isArray(issue.labels)
    ? Array.from(new Set(issue.labels.filter(Boolean)))
    : [];

  return (
    <Link
      className="surface group block p-5 transition-colors hover:border-[#cdd4dc] hover:bg-dark-elevated/40"
      href={`/issues/${issue.id}`}
    >
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <StatusBadge status={issue.status} />
            <span className="font-mono text-xs text-dark-muted">
              {issue.repoFullName}#{issue.issueNumber}
            </span>
            {hasSimilarityScore ? (
              <span className="badge-trim tabular-nums rounded-md border border-[#137333]/20 bg-[#e6f4ea] px-2 py-0.5 text-xs font-medium text-[#137333]">
                매칭 {scorePercent}%
              </span>
            ) : null}
            {issue.isAnalyzed ? (
              <span className="badge-trim rounded-md border border-brand-500/20 bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-600">
                분석 완료
              </span>
            ) : null}
          </div>

          <h3 className="mb-1.5 line-clamp-1 font-medium text-dark-text transition-colors group-hover:text-brand-500">
            {issue.title}
          </h3>
          {issue.summary ? (
            <p className="line-clamp-2 text-xs leading-5 text-dark-muted">{issue.summary}</p>
          ) : null}
          {issue.reason ? (
            <p className="mt-2 line-clamp-1 text-xs leading-5 text-ink-secondary">
              추천 근거: {issue.reason}
            </p>
          ) : null}
          {uniqueLabels.length > 0 ? (
            <div className="mt-2.5 flex flex-wrap gap-1.5">
              {uniqueLabels.map((label, idx) => (
                <span
                  className="badge-trim rounded border border-dark-border bg-dark-elevated px-2 py-0.5 text-[11px] text-dark-muted"
                  key={`${label}-${idx}`}
                >
                  {label}
                </span>
              ))}
            </div>
          ) : null}
        </div>
        <span className="mt-1 shrink-0 text-xs font-medium text-brand-500 transition-transform group-hover:translate-x-0.5">
          자세히 →
        </span>
      </div>
    </Link>
  );
}
