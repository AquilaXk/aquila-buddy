"use client";

import { Suspense, useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { ArrowLeft, Check, Copy, ExternalLink, FileCode, GitPullRequest, Square } from "@/components/icons";
import { ExternalBrowserLink } from "@/components/ExternalBrowserLink";
import { MarkdownPreview } from "@/components/MarkdownPreview";
import ProtectedLayout from "@/components/ProtectedLayout";
import { StatusBadge } from "@/components/StatusBadge";
import { StudioSplitPane } from "@/components/StudioSplitPane";
import {
  getIssueDetail,
  getIssueGuide,
  getIssuePseudoCode,
  getRecommendedIssueHistory,
  getRecommendedIssues,
  streamIssueGuide,
  StreamServerError,
  type IssueGuide,
  type IssuePseudoCode,
  type RecommendedIssue,
  type RecommendedIssueHistory
} from "@/lib/api";
import { highlightCodeLine } from "@/lib/highlightCodeLine";
import { resolveDynamicRouteId } from "@/lib/routes";
import { cn } from "@/lib/utils";

type IssueDetailIssue = RecommendedIssue | RecommendedIssueHistory;
type AnalysisTab = "guide" | "pseudo";

export default function IssueDetailPage() {
  return (
    <Suspense fallback={null}>
      <IssueDetailContent />
    </Suspense>
  );
}

function IssueDetailContent() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const issueId = resolveDynamicRouteId(params.id, searchParams.get("id"));
  const tabIdPrefix = useId();
  const guideTabRef = useRef<HTMLButtonElement>(null);
  const pseudoTabRef = useRef<HTMLButtonElement>(null);

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
  const [streamPseudoCode, setStreamPseudoCode] = useState("");
  const [streamSideEffects, setStreamSideEffects] = useState<string[]>([]);
  const [pseudo, setPseudo] = useState<IssuePseudoCode | null>(null);
  const [pseudoLoading, setPseudoLoading] = useState(false);
  const [pseudoError, setPseudoError] = useState<string | null>(null);
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const streamAbortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      streamAbortRef.current?.abort();
    };
  }, []);

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
      setStreamPseudoCode("");
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

  async function handleCopyText(text: string, key: string) {
    try {
      if (navigator.clipboard) {
        await navigator.clipboard.writeText(text);
      }
      setCopiedKey(key);
      window.setTimeout(() => {
        setCopiedKey((current) => (current === key ? null : current));
      }, 1600);
    } catch {
      // 클립보드 접근 불가 환경은 조용히 무시한다.
    }
  }

  function handleAbortStream() {
    streamAbortRef.current?.abort();
    streamAbortRef.current = null;
    setGuideLoading(false);
    setStreamStep(null);
  }

  async function handleGetGuide() {
    streamAbortRef.current?.abort();
    const controller = new AbortController();
    streamAbortRef.current = controller;

    setActiveTab("guide");
    setGuideLoading(true);
    setGuideError(null);
    setStreamStep("ANALYZING");
    setStreamFilePaths([]);
    setStreamGuideline("");
    setStreamPseudoCode("");
    setStreamSideEffects([]);

    try {
      await streamIssueGuide(
        issueId,
        {
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
              } else if (event.step === "PSEUDO_CODE") {
                setStreamPseudoCode((prev) => prev + event.chunk);
              }
            }
            if (event.sideEffects && Array.isArray(event.sideEffects) && event.sideEffects.length > 0) {
              setStreamSideEffects(event.sideEffects);
            }
          },
          onComplete: (completedGuide) => {
            setGuide(normalizeIssueGuide(completedGuide));
          }
        },
        controller.signal
      );
    } catch (streamCaught) {
      if (controller.signal.aborted || (streamCaught instanceof DOMException && streamCaught.name === "AbortError")) {
        return;
      }
      if (streamCaught instanceof StreamServerError) {
        setGuideError(streamCaught.message || "가이드 스트리밍 중 오류가 발생했습니다.");
        return;
      }
      try {
        setGuide(normalizeIssueGuide(await getIssueGuide(issueId)));
      } catch (caught) {
        setGuideError(caught instanceof Error ? caught.message : "가이드를 불러오지 못했습니다.");
      }
    } finally {
      if (streamAbortRef.current === controller) {
        streamAbortRef.current = null;
      }
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

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>, current: AnalysisTab) {
    let nextTab: AnalysisTab | null = null;
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      nextTab = current === "guide" ? "pseudo" : "guide";
    } else if (event.key === "Home") {
      nextTab = "guide";
    } else if (event.key === "End") {
      nextTab = "pseudo";
    }

    if (!nextTab) {
      return;
    }
    event.preventDefault();
    setActiveTab(nextTab);
    if (nextTab === "guide") {
      guideTabRef.current?.focus();
    } else {
      pseudoTabRef.current?.focus();
    }
  }

  if (loading) {
    return (
      <ProtectedLayout>
        <div className="space-y-4">
          <div className="h-20 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa]" />
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="h-64 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa] lg:col-span-5" />
            <div className="h-64 animate-pulse rounded-lg border border-[#d1d9e0] bg-[#f6f8fa] lg:col-span-7" />
          </div>
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !issue) {
    return (
      <ProtectedLayout>
        <section className="primer-box py-16 text-center">
          <h1 className="text-[20px] font-semibold text-[#1f2328]">이슈를 찾을 수 없습니다.</h1>
          <p className="mt-2 text-[13.5px] text-[#59636e]">
            추천 이력에서 선택한 이슈 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-5 inline-flex items-center gap-1.5 text-xs font-medium text-[#0969da] hover:underline"
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
  const guideTabId = `${tabIdPrefix}-tab-guide`;
  const pseudoTabId = `${tabIdPrefix}-tab-pseudo`;
  const guidePanelId = `${tabIdPrefix}-panel-guide`;
  const pseudoPanelId = `${tabIdPrefix}-panel-pseudo`;

  const leftContextPane = (
    <aside className="primer-box divide-y divide-[#d1d9e0] overflow-hidden">
      <div className="primer-box-header flex items-center justify-between px-4 py-3">
        <span className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
          이슈 컨텍스트
        </span>
        <span className="tabular-nums font-mono text-[12px] font-medium text-[#59636e]">
          #{issue.issueNumber}
        </span>
      </div>

      {"reason" in issue && issue.reason ? (
        <div className="p-4">
          <div className="rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-3.5">
            <h2 className="text-[13px] font-semibold text-[#1f2328]">추천 이유</h2>
            <p className="mt-1.5 text-[13.5px] leading-[1.55] text-[#59636e]">{issue.reason}</p>
          </div>
        </div>
      ) : null}

      {issue.labels.length > 0 ? (
        <div className="p-4">
          <h2 className="mb-2 text-[13px] font-semibold text-[#1f2328]">라벨</h2>
          <div className="flex flex-wrap gap-1.5">
            {issue.labels.map((label) => (
              <span
                className="badge-trim rounded-full border border-[#d1d9e0] bg-[#f6f8fa] px-2.5 py-0.5 font-mono text-[11.5px] font-medium text-[#59636e]"
                key={label}
              >
                {label}
              </span>
            ))}
          </div>
        </div>
      ) : null}

      <div className="p-4">
        <FilePathList
          copiedKey={copiedKey}
          filePaths={activeFilePaths}
          onCopy={(filePath) => handleCopyText(filePath, `path:${filePath}`)}
        />
      </div>

      <div className="bg-[#f6f8fa] p-4">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-[13px] font-semibold text-[#1f2328]">PR 초안 생성</h2>
          <Link
            className="btn-secondary focus-ring px-2.5 py-1 text-xs"
            href={prDraftHref}
          >
            작업 브랜치로 이동
          </Link>
        </div>
        <div className="mt-2">
          <span className="primer-branch-pill">
            main ← {buildHeadBranch(issue)}
          </span>
        </div>
      </div>
    </aside>
  );

  const rightAnalysisPane = (
    <section className="primer-box overflow-hidden">
      <div className="primer-box-header flex flex-wrap items-center justify-between gap-3 px-4 py-2.5">
        <span className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
          코드 맥락 분석
        </span>
        <div className="flex items-center gap-2">
          <button
            className="btn-secondary focus-ring px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-50"
            disabled={guideLoading}
            onClick={handleGetGuide}
            type="button"
          >
            <span className="button-text">{guideLoading ? "분석 중..." : "힌트 보기"}</span>
          </button>
          {guideLoading ? (
            <button
              aria-label="스트리밍 중지"
              className="focus-ring inline-flex items-center gap-1 rounded-md border border-[#ff8182]/40 bg-[#ffebe9] px-2.5 py-1.5 text-xs font-medium text-[#d1242f] hover:bg-[#ffd8d5]"
              onClick={handleAbortStream}
              type="button"
            >
              <Square className="h-3 w-3" />
              <span>중지</span>
            </button>
          ) : null}
          <button
            className="btn-secondary focus-ring px-3 py-1.5 text-xs disabled:cursor-not-allowed disabled:opacity-50"
            disabled={pseudoLoading}
            onClick={handleGetPseudo}
            type="button"
          >
            <span className="button-text">{pseudoLoading ? "분석 중..." : "코드 힌트 보기"}</span>
          </button>
        </div>
      </div>

      <div
        aria-label="코드 분석 탭"
        className="primer-underline-nav bg-[#f6f8fa] px-4"
        role="tablist"
      >
        <button
          aria-controls={guidePanelId}
          aria-selected={activeTab === "guide"}
          className={cn(
            "focus-ring -mb-px border-b-2 px-3 py-2.5 text-[13px] transition-colors",
            activeTab === "guide"
              ? "border-[#fd8c73] font-semibold text-[#1f2328]"
              : "border-transparent font-medium text-[#59636e] hover:border-[#d1d9e0] hover:text-[#1f2328]"
          )}
          id={guideTabId}
          onClick={() => setActiveTab("guide")}
          onKeyDown={(event) => handleTabKeyDown(event, "guide")}
          ref={guideTabRef}
          role="tab"
          tabIndex={activeTab === "guide" ? 0 : -1}
          type="button"
        >
          <span className="button-text">수정 가이드</span>
        </button>
        <button
          aria-controls={pseudoPanelId}
          aria-selected={activeTab === "pseudo"}
          className={cn(
            "focus-ring -mb-px border-b-2 px-3 py-2.5 text-[13px] transition-colors",
            activeTab === "pseudo"
              ? "border-[#fd8c73] font-semibold text-[#1f2328]"
              : "border-transparent font-medium text-[#59636e] hover:border-[#d1d9e0] hover:text-[#1f2328]"
          )}
          id={pseudoTabId}
          onClick={() => setActiveTab("pseudo")}
          onKeyDown={(event) => handleTabKeyDown(event, "pseudo")}
          ref={pseudoTabRef}
          role="tab"
          tabIndex={activeTab === "pseudo" ? 0 : -1}
          type="button"
        >
          <span className="button-text">의사 코드</span>
        </button>
      </div>

      <div
        aria-labelledby={guideTabId}
        className="divide-y divide-[#d1d9e0]"
        hidden={activeTab !== "guide"}
        id={guidePanelId}
        role="tabpanel"
      >
        {guideError ? (
          <div className="flex flex-wrap items-center justify-between gap-2 bg-[#ffebe9] px-4 py-3 text-xs font-medium text-[#d1242f]" role="alert">
            <span>{guideError}</span>
            <button
              className="focus-ring rounded-md border border-[#ff8182]/40 bg-white px-2.5 py-1 text-xs font-semibold text-[#d1242f] hover:bg-[#ffebe9]"
              onClick={handleGetGuide}
              type="button"
            >
              다시 시도
            </button>
          </div>
        ) : null}

        {guideLoading && !guide ? (
          <div className="space-y-3 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[#d1d9e0] pb-3">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2 w-2">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[#0969da] opacity-75" />
                  <span className="relative inline-flex h-2 w-2 rounded-full bg-[#0969da]" />
                </span>
                <span className="text-xs font-semibold text-[#0969da]">
                  {streamStep === "ANALYZING" && "저장소 코드 맥락 분석 중..."}
                  {streamStep === "FILES" && "수정 대상 파일 분석 완료"}
                  {streamStep === "GUIDELINE" && "가이드라인 실시간 스트리밍 중..."}
                  {streamStep === "PSEUDO_CODE" && "의사 코드 실시간 스트리밍 중..."}
                  {streamStep === "SIDE_EFFECTS" && "주의 사항 점검 중..."}
                  {!streamStep && "가이드 실시간 스트리밍 중..."}
                </span>
              </div>
              <div className="flex items-center gap-1.5 font-mono text-[11.5px] text-[#59636e]">
                <span className={streamStep === "ANALYZING" ? "font-semibold text-[#0969da]" : ""}>1.분석</span>
                <span>·</span>
                <span className={streamStep === "FILES" ? "font-semibold text-[#0969da]" : streamFilePaths.length > 0 ? "font-semibold text-[#1a7f37]" : ""}>2.파일</span>
                <span>·</span>
                <span className={streamStep === "GUIDELINE" ? "font-semibold text-[#0969da]" : streamGuideline ? "font-semibold text-[#1a7f37]" : ""}>3.가이드</span>
                <span>·</span>
                <span className={streamStep === "PSEUDO_CODE" ? "font-semibold text-[#0969da]" : streamPseudoCode ? "font-semibold text-[#1a7f37]" : ""}>4.코드</span>
                <span>·</span>
                <span className={streamStep === "SIDE_EFFECTS" ? "font-semibold text-[#0969da]" : streamSideEffects.length > 0 ? "font-semibold text-[#1a7f37]" : ""}>5.주의점</span>
              </div>
            </div>

            {streamGuideline ? (
              <div className="rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4">
                <p className="mb-1.5 text-[13px] font-semibold text-[#1f2328]">가이드라인</p>
                <p className="max-w-[70ch] whitespace-pre-wrap text-[14px] leading-[1.65] text-[#1f2328]">
                  {streamGuideline}
                  <span className="ml-0.5 inline-block h-3.5 w-1 animate-pulse bg-[#0969da] align-middle" />
                </p>
              </div>
            ) : null}

            {streamPseudoCode ? (
              <div className="rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4">
                <p className="mb-1.5 text-[13px] font-semibold text-[#1f2328]">의사 코드 초안</p>
                <pre className="max-h-60 overflow-auto whitespace-pre-wrap font-mono text-[13px] leading-[1.6] text-[#1f2328]">
                  {streamPseudoCode}
                </pre>
              </div>
            ) : null}

            {streamSideEffects.length > 0 ? (
              <div className="rounded-md border border-[#9a6700]/25 bg-[#fff8c5] p-3.5 text-[#9a6700]">
                <p className="mb-1 text-[13px] font-semibold text-[#1f2328]">주의 사항</p>
                <ul className="list-disc space-y-1 pl-4 text-[13px] leading-[1.55] text-[#1f2328]">
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
              <div className="rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4">
                <h3 className="mb-2.5 text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
                  가이드라인
                </h3>
                <div className="max-w-[70ch]">
                  <MarkdownPreview content={guide.guideline} />
                </div>
              </div>
            </div>
            {guide.sideEffects.length > 0 ? (
              <div className="border-t border-[#9a6700]/25 bg-[#fff8c5]/60 p-5">
                <h3 className="mb-2 text-[14px] font-semibold tracking-[-0.015em] text-[#9a6700]">
                  주의 사항
                </h3>
                <ul className="max-w-[70ch] list-disc space-y-1.5 pl-4 text-[13.5px] leading-[1.6] text-[#1f2328]">
                  {guide.sideEffects.map((sideEffect) => (
                    <li key={sideEffect}>{sideEffect}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </>
        ) : !guideLoading ? (
          <div className="px-5 py-12 text-center">
            <p className="text-[13.5px] text-[#59636e]">
              상단 툴바의 <strong className="font-semibold text-[#1f2328]">힌트 보기</strong>를 실행하면 저장소 코드 맥락 기반의 수정 가이드와 주의 사항이 스트리밍됩니다.
            </p>
          </div>
        ) : null}
      </div>

      <div
        aria-labelledby={pseudoTabId}
        className="divide-y divide-[#d1d9e0]"
        hidden={activeTab !== "pseudo"}
        id={pseudoPanelId}
        role="tabpanel"
      >
        {pseudoError ? (
          <div className="flex flex-wrap items-center justify-between gap-2 bg-[#ffebe9] px-4 py-3 text-xs font-medium text-[#d1242f]" role="alert">
            <span>{pseudoError}</span>
            <button
              className="focus-ring rounded-md border border-[#ff8182]/40 bg-white px-2.5 py-1 text-xs font-semibold text-[#d1242f] hover:bg-[#ffebe9]"
              onClick={handleGetPseudo}
              type="button"
            >
              다시 시도
            </button>
          </div>
        ) : null}

        {pseudo ? (
          <div className="p-4">
            <div className="mb-2.5 flex items-center justify-end">
              <button
                className="btn-secondary focus-ring inline-flex items-center gap-1.5 px-2.5 py-1 text-xs"
                onClick={() => handleCopyText(pseudo.pseudoCode, "pseudo")}
                type="button"
              >
                {copiedKey === "pseudo" ? (
                  <>
                    <Check className="h-3.5 w-3.5 text-[#1a7f37]" />
                    <span>복사됨 ✓</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3.5 w-3.5" />
                    <span>의사 코드 복사</span>
                  </>
                )}
              </button>
            </div>
            <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4 font-mono text-[13px] leading-[1.6] text-[#1f2328]">
              {pseudo.pseudoCode.split("\n").map((line, idx) => (
                <div key={idx}>{highlightCodeLine(line) || " "}</div>
              ))}
            </pre>
          </div>
        ) : (
          <div className="px-5 py-12 text-center">
            <p className="text-[13.5px] text-[#59636e]">
              상단 툴바의 <strong className="font-semibold text-[#1f2328]">코드 힌트 보기</strong>를 실행하면 타겟 파일에 대한 의사 코드 제안이 표시됩니다.
            </p>
          </div>
        )}
      </div>
    </section>
  );

  return (
    <ProtectedLayout>
      <header className="mb-6 border-b border-[#d1d9e0] pb-5">
        <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2.5">
            <Link
              className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-[#59636e] transition-colors hover:text-[#0969da]"
              href="/issues"
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              <span>추천 이슈 목록</span>
            </Link>
            <span className="text-[#d1d9e0]">/</span>
            <StatusBadge status={issue.status} />
            <ExternalBrowserLink
              className="inline-flex items-center gap-1 font-mono text-[12px] font-medium tabular-nums text-[#59636e] transition-colors hover:text-[#0969da]"
              href={githubUrl}
            >
              <span>{issue.repoFullName} #{issue.issueNumber}</span>
              <ExternalLink className="h-3 w-3" />
            </ExternalBrowserLink>
            {issue.score > 0 ? (
              <span className="badge-trim tabular-nums rounded-full border border-[#1a7f37]/30 bg-[#dafbe1] px-2.5 py-0.5 font-mono text-xs font-semibold text-[#1a7f37]">
                매칭 {scorePercent}%
              </span>
            ) : null}
            {"isAnalyzed" in issue ? (
              <span
                className={cn(
                  "badge-trim rounded-full border px-2.5 py-0.5 text-xs font-medium",
                  issue.isAnalyzed
                    ? "border-[#0969da]/25 bg-[#ddf4ff] text-[#0969da]"
                    : "border-[#d1d9e0] bg-[#f6f8fa] text-[#59636e]"
                )}
              >
                {getIssueAnalysisLabel(issue)}
              </span>
            ) : null}
          </div>

          <Link
            className="btn-primer-green focus-ring inline-flex shrink-0 items-center gap-1.5 px-3.5 py-2 text-[13px]"
            href={prDraftHref}
          >
            <GitPullRequest className="h-3.5 w-3.5" />
            <span className="button-text">PR 초안 만들기</span>
          </Link>
        </div>

        <h1 className="text-[22px] font-semibold leading-[1.3] tracking-[-0.025em] text-[#1f2328]">
          {issue.title}
        </h1>
      </header>

      <StudioSplitPane
        defaultRatio={42}
        leftLabel="이슈 컨텍스트"
        leftPane={leftContextPane}
        rightPane={rightAnalysisPane}
        storageKey="issue-detail"
      />
    </ProtectedLayout>
  );
}

function FilePathList({
  filePaths,
  copiedKey,
  onCopy,
}: {
  filePaths?: string[] | null;
  copiedKey: string | null;
  onCopy: (path: string) => void;
}) {
  const safeFilePaths = normalizeStringArray(filePaths);

  return (
    <div>
      <h2 className="mb-2 text-[13px] font-semibold text-[#1f2328]">수정 대상 파일</h2>
      {safeFilePaths.length > 0 ? (
        <ul className="divide-y divide-[#d1d9e0] rounded-md border border-[#d1d9e0] bg-[#f6f8fa]">
          {safeFilePaths.map((filePath) => (
            <li
              className="flex items-center justify-between gap-2 px-3 py-2 font-mono text-[12px] text-[#1f2328]"
              key={filePath}
            >
              <span className="flex min-w-0 items-center gap-2">
                <FileCode className="h-3.5 w-3.5 shrink-0 text-[#0969da]" aria-hidden="true" />
                <span className="min-w-0 break-all">{filePath}</span>
              </span>
              <button
                aria-label={`${filePath} 경로 복사`}
                className="focus-ring shrink-0 rounded-md border border-[#d1d9e0] bg-white px-2 py-0.5 font-sans text-[11px] font-medium text-[#59636e] transition-colors hover:border-[#b9c3ce] hover:text-[#1f2328]"
                onClick={() => onCopy(filePath)}
                type="button"
              >
                {copiedKey === `path:${filePath}` ? "복사됨" : "복사"}
              </button>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-[13px] text-[#59636e]">수정 대상 파일 없음</p>
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
  try {
    const historyIssues = await getRecommendedIssueHistory();
    const matchedHistory = historyIssues.find((item) => item.id === issueId);
    if (matchedHistory) {
      return matchedHistory;
    }
  } catch {
    // 추천 이력 조회 실패 시 현재 추천 목록에서 조회를 시도한다.
  }

  try {
    const recommendedIssues = await getRecommendedIssues();
    return recommendedIssues.find((item) => item.id === issueId) ?? null;
  } catch {
    return null;
  }
}

function buildHeadBranch(issue: IssueDetailIssue) {
  return `feat/issue-${issue.issueNumber}`;
}

function buildPullRequestDraftHref(issue: IssueDetailIssue) {
  const params = new URLSearchParams({
    repo: issue.repoFullName,
    issueNumber: String(issue.issueNumber),
    issueId: issue.id,
    issueTitle: issue.title,
    sourceIssueId: issue.id,
    base: "main",
    head: buildHeadBranch(issue)
  });

  return `/pr-draft?${params.toString()}`;
}

function getIssueAnalysisLabel(issue: RecommendedIssueHistory) {
  if (issue.isAnalyzed) {
    return "분석 완료";
  }

  return "분석 전";
}
