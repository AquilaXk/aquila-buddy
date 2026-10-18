"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ExternalLink, GitPullRequest } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import {
  getIssueDetail,
  getIssueGuide,
  getIssuePseudoCode,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  streamIssueGuide,
  type IssueGuide,
  type IssuePseudoCode,
  type RecommendedIssue,
  type RecommendedIssueHistory
} from "@/lib/api";

type IssueDetailIssue = RecommendedIssue | RecommendedIssueHistory;
type AnalysisTab = "guide" | "pseudo";

export default function IssueDetailPage() {
  const params = useParams<{ id: string }>();
  const issueId = params.id;
  const [issue, setIssue] = useState<IssueDetailIssue | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [activeTab, setActiveTab] = useState<AnalysisTab>("guide");
  const [guide, setGuide] = useState<IssueGuide | null>(null);
  const [guideLoading, setGuideLoading] = useState(false);
  const [guideError, setGuideError] = useState<string | null>(null);
  const [streamStep, setStreamStep] = useState<string | null>(null);
  const [streamFilePaths, setStreamFilePaths] = useState<string[]>([]);
  const [streamGuideline, setStreamGuideline] = useState("");
  const [streamSideEffects, setStreamSideEffects] = useState<string[]>([]);
  const [pseudo, setPseudo] = useState<IssuePseudoCode | null>(null);
  const [pseudoLoading, setPseudoLoading] = useState(false);
  const [pseudoError, setPseudoError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    async function loadIssue() {
      setLoading(true);
      setNotFound(false);

      const foundIssue = await loadIssueById(issueId);

      if (!isMounted) {
        return;
      }

      setIssue(foundIssue);
      setNotFound(!foundIssue);
      setGuide(null);
      setGuideError(null);
      setStreamStep(null);
      setStreamFilePaths([]);
      setStreamGuideline("");
      setStreamSideEffects([]);
      setPseudo(null);
      setPseudoError(null);
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
    setActiveTab("guide");
    setGuideLoading(true);
    setGuideError(null);
    setStreamStep("ANALYZING");
    setStreamFilePaths([]);
    setStreamGuideline("");
    setStreamSideEffects([]);

    try {
      await streamIssueGuide(issueId, {
        onChunk: (event) => {
          if (event.step) {
            setStreamStep(event.step);
          }
          if (event.filePaths && Array.isArray(event.filePaths) && event.filePaths.length > 0) {
            setStreamFilePaths(event.filePaths);
          }
          if (event.chunk) {
            if (event.step === "GUIDELINE") {
              setStreamGuideline((prev) => prev + event.chunk);
            }
          }
          if (event.sideEffects && Array.isArray(event.sideEffects) && event.sideEffects.length > 0) {
            setStreamSideEffects(event.sideEffects);
          }
        },
        onComplete: (completedGuide) => {
          setGuide(normalizeIssueGuide(completedGuide));
        }
      });
    } catch {
      try {
        setGuide(normalizeIssueGuide(await getIssueGuide(issueId)));
      } catch (caught) {
        setGuideError(caught instanceof Error ? caught.message : "가이드를 불러오지 못했습니다.");
      }
    } finally {
      setGuideLoading(false);
      setStreamStep(null);
    }
  }

  async function handleGetPseudo() {
    setActiveTab("pseudo");
    setPseudoLoading(true);
    setPseudoError(null);

    try {
      setPseudo(normalizeIssuePseudoCode(await getIssuePseudoCode(issueId)));
    } catch (caught) {
      setPseudoError(caught instanceof Error ? caught.message : "의사 코드를 불러오지 못했습니다.");
    } finally {
      setPseudoLoading(false);
    }
  }

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="surface h-20 animate-pulse bg-neutral-subtle" />
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="surface h-64 animate-pulse bg-neutral-subtle lg:col-span-5" />
            <div className="surface h-64 animate-pulse bg-neutral-subtle lg:col-span-7" />
          </div>
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !issue) {
    return (
      <ProtectedLayout>
        <section className="surface py-16 text-center">
          <h1 className="text-lg font-semibold text-ink-primary">이슈를 찾을 수 없습니다.</h1>
          <p className="mt-2 text-xs text-ink-secondary">
            추천 이력에서 선택한 이슈 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-brand-500 hover:text-brand-600"
            href="/issues"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            <span>추천 이슈 목록</span>
          </Link>
        </section>
      </ProtectedLayout>
    );
  }

  const githubUrl = `https://github.com/${issue.repoFullName}/issues/${issue.issueNumber}`;
  const scorePercent = Math.round(issue.score * 100);
  const prDraftHref = buildPullRequestDraftHref(issue);
  const activeFilePaths = guide?.filePaths?.length
    ? guide.filePaths
    : pseudo?.filePaths?.length
      ? pseudo.filePaths
      : streamFilePaths;

  return (
    <ProtectedLayout>
      <header className="mb-6 border-b border-neutral-border pb-5">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <Link
              className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-ink-secondary transition-colors hover:text-ink-primary"
              href="/issues"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>추천 이슈 목록</span>
            </Link>
            <span className="text-neutral-borderStrong">/</span>
            <StatusBadge status={issue.status} />
            <a
              className="inline-flex items-center gap-1 font-mono text-xs text-ink-secondary transition-colors hover:text-brand-500"
              href={githubUrl}
              rel="noopener noreferrer"
              target="_blank"
            >
              <span>{issue.repoFullName} #{issue.issueNumber}</span>
              <ExternalLink className="h-3 w-3" />
            </a>
            {issue.score > 0 ? (
              <span className="badge-trim tabular-nums rounded border border-[#137333]/20 bg-[#e6f4ea] px-2 py-0.5 font-mono text-xs font-medium text-[#137333]">
                매칭 {scorePercent}%
              </span>
            ) : null}
            {"isAnalyzed" in issue ? (
              <span className="badge-trim rounded border border-brand-500/20 bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-600">
                {getIssueAnalysisLabel(issue)}
              </span>
            ) : null}
          </div>

          <Link
            className="btn-primary focus-ring inline-flex shrink-0 items-center gap-1.5 px-3.5 py-2 text-xs"
            href={prDraftHref}
          >
            <GitPullRequest className="h-3.5 w-3.5" />
            <span className="button-text">PR 초안 만들기</span>
          </Link>
        </div>

        <h1 className="text-xl font-semibold leading-snug tracking-[-0.02em] text-ink-primary">
          {issue.title}
        </h1>
      </header>

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <aside className="surface divide-y divide-neutral-border overflow-hidden lg:col-span-5">
          {"reason" in issue && issue.reason ? (
            <div className="p-4">
              <h2 className="text-xs font-semibold text-ink-primary">추천 이유</h2>
              <p className="mt-1.5 text-xs leading-relaxed text-ink-secondary">{issue.reason}</p>
            </div>
          ) : null}

          {issue.labels.length > 0 ? (
            <div className="p-4">
              <h2 className="mb-2 text-xs font-semibold text-ink-primary">라벨</h2>
              <div className="flex flex-wrap gap-1.5">
                {issue.labels.map((label) => (
                  <span
                    className="badge-trim rounded border border-neutral-border bg-neutral-subtle px-2 py-0.5 font-mono text-[11px] text-ink-secondary"
                    key={label}
                  >
                    {label}
                  </span>
                ))}
              </div>
            </div>
          ) : null}

          <div className="p-4">
            <FilePathList filePaths={activeFilePaths} />
          </div>

          <div className="bg-neutral-subtle p-4">
            <div className="flex items-center justify-between gap-2">
              <h2 className="text-xs font-semibold text-ink-primary">PR 초안 생성</h2>
              <Link
                className="focus-ring text-xs font-medium text-brand-500 transition-colors hover:text-brand-600"
                href={prDraftHref}
              >
                작업 브랜치로 이동
              </Link>
            </div>
            <p className="mt-1 font-mono text-[11px] text-ink-muted">
              main ← {buildHeadBranch(issue)}
            </p>
          </div>
        </aside>

        <section className="surface overflow-hidden lg:col-span-7">
          <div className="sheet-header flex flex-wrap items-center justify-between gap-3 px-4 py-2.5">
            <div className="flex items-center gap-1" role="tablist" aria-label="코드 분석 탭">
              <button
                aria-selected={activeTab === "guide"}
                className={`focus-ring rounded px-3 py-1 text-xs font-medium transition-colors ${
                  activeTab === "guide"
                    ? "bg-[#121317] text-white"
                    : "text-ink-secondary hover:bg-neutral-hover hover:text-ink-primary"
                }`}
                onClick={() => setActiveTab("guide")}
                role="tab"
                type="button"
              >
                <span className="button-text">수정 가이드</span>
              </button>
              <button
                aria-selected={activeTab === "pseudo"}
                className={`focus-ring rounded px-3 py-1 text-xs font-medium transition-colors ${
                  activeTab === "pseudo"
                    ? "bg-[#121317] text-white"
                    : "text-ink-secondary hover:bg-neutral-hover hover:text-ink-primary"
                }`}
                onClick={() => setActiveTab("pseudo")}
                role="tab"
                type="button"
              >
                <span className="button-text">의사 코드</span>
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                className="btn-secondary focus-ring px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                disabled={guideLoading}
                onClick={handleGetGuide}
                type="button"
              >
                <span className="button-text">{guideLoading ? "분석 중..." : "힌트 보기"}</span>
              </button>
              <button
                className="btn-secondary focus-ring px-3 py-1 text-xs disabled:cursor-not-allowed disabled:opacity-50"
                disabled={pseudoLoading}
                onClick={handleGetPseudo}
                type="button"
              >
                <span className="button-text">{pseudoLoading ? "분석 중..." : "코드 힌트 보기"}</span>
              </button>
            </div>
          </div>

          {activeTab === "guide" ? (
            <div className="divide-y divide-neutral-border">
              {guideError ? (
                <div className="bg-[#fce8e6] px-4 py-3 text-xs font-medium text-[#c5221f]">
                  {guideError}
                </div>
              ) : null}

              {guideLoading && !guide ? (
                <div className="space-y-3 p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2 border-b border-neutral-border pb-3">
                    <div className="flex items-center gap-2">
                      <span className="relative flex h-2 w-2">
                        <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-400 opacity-75" />
                        <span className="relative inline-flex h-2 w-2 rounded-full bg-brand-500" />
                      </span>
                      <span className="text-xs font-medium text-brand-600">
                        {streamStep === "ANALYZING" && "저장소 코드 맥락 분석 중..."}
                        {streamStep === "FILES" && "수정 대상 파일 분석 완료"}
                        {streamStep === "GUIDELINE" && "가이드라인 실시간 스트리밍 중..."}
                        {streamStep === "PSEUDO_CODE" && "의사 코드 생성 중..."}
                        {streamStep === "SIDE_EFFECTS" && "주의 사항 점검 중..."}
                        {!streamStep && "가이드 실시간 스트리밍 중..."}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 font-mono text-[11px] text-ink-muted">
                      <span className={streamStep === "ANALYZING" ? "font-semibold text-brand-600" : ""}>1.분석</span>
                      <span>·</span>
                      <span className={streamStep === "FILES" ? "font-semibold text-brand-600" : streamFilePaths.length > 0 ? "font-semibold text-[#137333]" : ""}>2.파일</span>
                      <span>·</span>
                      <span className={streamStep === "GUIDELINE" ? "font-semibold text-brand-600" : streamGuideline ? "font-semibold text-[#137333]" : ""}>3.가이드</span>
                      <span>·</span>
                      <span className={streamStep === "SIDE_EFFECTS" ? "font-semibold text-brand-600" : streamSideEffects.length > 0 ? "font-semibold text-[#137333]" : ""}>4.주의점</span>
                    </div>
                  </div>

                  {streamGuideline ? (
                    <div>
                      <p className="mb-1.5 text-xs font-semibold text-ink-primary">가이드라인</p>
                      <p className="whitespace-pre-wrap text-xs leading-relaxed text-ink-secondary">
                        {streamGuideline}
                        <span className="ml-0.5 inline-block h-3.5 w-1 animate-pulse bg-brand-500 align-middle" />
                      </p>
                    </div>
                  ) : null}

                  {streamSideEffects.length > 0 ? (
                    <div className="rounded border border-[#b06000]/20 bg-[#fef7e0] p-3">
                      <p className="mb-1 text-xs font-semibold text-[#b06000]">주의 사항</p>
                      <ul className="list-disc space-y-1 pl-4 text-xs text-ink-primary">
                        {streamSideEffects.map((se) => (
                          <li key={se}>{se}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </div>
              ) : null}

              {guide ? (
                <>
                  <div className="p-5">
                    <h3 className="mb-2 text-xs font-semibold text-ink-primary">가이드라인</h3>
                    <p className="whitespace-pre-wrap text-sm leading-6 text-ink-primary">
                      {guide.guideline}
                    </p>
                  </div>
                  {guide.sideEffects.length > 0 ? (
                    <div className="bg-[#fef7e0]/60 p-5">
                      <h3 className="mb-2 text-xs font-semibold text-[#b06000]">주의 사항</h3>
                      <ul className="list-disc space-y-1.5 pl-4 text-xs leading-relaxed text-ink-primary">
                        {guide.sideEffects.map((sideEffect) => (
                          <li key={sideEffect}>{sideEffect}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </>
              ) : !guideLoading ? (
                <div className="px-5 py-12 text-center">
                  <p className="text-xs text-ink-secondary">
                    상단 툴바의 <strong className="font-medium text-ink-primary">힌트 보기</strong>를 실행하면 저장소 코드 맥락 기반의 수정 가이드와 주의 사항이 스트리밍됩니다.
                  </p>
                </div>
              ) : null}
            </div>
          ) : (
            <div className="divide-y divide-neutral-border">
              {pseudoError ? (
                <div className="bg-[#fce8e6] px-4 py-3 text-xs font-medium text-[#c5221f]">
                  {pseudoError}
                </div>
              ) : null}

              {pseudo ? (
                <div className="p-4">
                  <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded border border-neutral-border bg-neutral-subtle p-4 font-mono text-xs leading-6 text-ink-primary">
                    {pseudo.pseudoCode}
                  </pre>
                </div>
              ) : (
                <div className="px-5 py-12 text-center">
                  <p className="text-xs text-ink-secondary">
                    상단 툴바의 <strong className="font-medium text-ink-primary">코드 힌트 보기</strong>를 실행하면 타겟 파일에 대한 의사 코드 제안이 표시됩니다.
                  </p>
                </div>
              )}
            </div>
          )}
        </section>
      </div>
    </ProtectedLayout>
  );
}

function FilePathList({ filePaths }: { filePaths?: string[] | null }) {
  const safeFilePaths = normalizeStringArray(filePaths);

  return (
    <div>
      <h2 className="mb-2 text-xs font-semibold text-ink-primary">수정 대상 파일</h2>
      {safeFilePaths.length > 0 ? (
        <ul className="divide-y divide-neutral-border rounded border border-neutral-border bg-neutral-subtle">
          {safeFilePaths.map((filePath) => (
            <li
              className="break-all px-3 py-1.5 font-mono text-xs text-ink-primary"
              key={filePath}
            >
              {filePath}
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-xs text-ink-muted">수정 대상 파일 없음</p>
      )}
    </div>
  );
}

function normalizeIssueGuide(guide: IssueGuide): IssueGuide {
  return {
    ...guide,
    filePaths: normalizeStringArray(guide.filePaths),
    sideEffects: normalizeStringArray(guide.sideEffects)
  };
}

function normalizeIssuePseudoCode(pseudo: IssuePseudoCode): IssuePseudoCode {
  return {
    ...pseudo,
    filePaths: normalizeStringArray(pseudo.filePaths)
  };
}

function normalizeStringArray(value: unknown) {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === "string" && item.trim().length > 0)
    : [];
}

async function loadIssueById(issueId: string): Promise<IssueDetailIssue | null> {
  try {
    return await getIssueDetail(issueId);
  } catch {
    return fallbackIssueLookup(issueId);
  }
}

async function fallbackIssueLookup(issueId: string): Promise<IssueDetailIssue | null> {
  const [historyResult, recommendationsResult] = await Promise.allSettled([
    getRecommendedIssueHistory(),
    getRecommendedIssues()
  ]);
  const history = historyResult.status === "fulfilled" ? historyResult.value : [];
  const recommendations = recommendationsResult.status === "fulfilled" ? recommendationsResult.value : [];

  return [...history, ...recommendations].find((item) => item.id === issueId) ?? null;
}

function getIssueAnalysisLabel(issue: RecommendedIssueHistory) {
  return issue.isAnalyzed ? "분석 완료" : "분석 전";
}

function buildPullRequestDraftHref(issue: IssueDetailIssue) {
  const params = new URLSearchParams({
    repo: issue.repoFullName,
    issue: String(issue.issueNumber),
    issueNumber: String(issue.issueNumber),
    issueId: issue.id,
    issueTitle: issue.title,
    sourceIssueId: issue.id,
    base: "main",
    head: buildHeadBranch(issue)
  });

  return `/pr-draft?${params.toString()}`;
}

function buildHeadBranch(issue: IssueDetailIssue) {
  const slug = issue.title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40)
    .replace(/-+$/g, "");

  return `feat/${slug || `issue-${issue.issueNumber}`}`;
}
