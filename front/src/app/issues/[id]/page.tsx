"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ExternalLink, Lightbulb, Code2, GitPullRequest } from "lucide-react";
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

export default function IssueDetailPage() {
  const params = useParams<{ id: string }>();
  const issueId = params.id;
  const [issue, setIssue] = useState<IssueDetailIssue | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
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
          <div className="surface h-24 animate-pulse bg-dark-elevated" />
          <div className="surface h-32 animate-pulse bg-dark-elevated" />
          <div className="surface h-32 animate-pulse bg-dark-elevated" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !issue) {
    return (
      <ProtectedLayout>
        <section className="surface py-20 text-center">
          <h1 className="text-2xl font-medium text-dark-text">이슈를 찾을 수 없습니다.</h1>
          <p className="mt-3 text-sm text-dark-muted">
            추천 이력에서 선택한 이슈 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-6 inline-flex items-center gap-1.5 text-sm font-medium text-brand-500 hover:text-brand-600"
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
  const prDraftHref = buildPullRequestDraftHref(issue);

  return (
    <ProtectedLayout>
      <div className="mb-6">
        <Link
          className="focus-ring inline-flex items-center gap-1.5 text-sm font-medium text-dark-muted transition-colors hover:text-dark-text"
          href="/issues"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>추천 이슈 목록</span>
        </Link>
      </div>

      <section className="surface mb-5 p-6">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <StatusBadge status={issue.status} />
          <a
            className="inline-flex items-center gap-1 font-mono text-xs text-dark-muted transition-colors hover:text-brand-500"
            href={githubUrl}
            rel="noopener noreferrer"
            target="_blank"
          >
            <span>{issue.repoFullName} #{issue.issueNumber}</span>
            <ExternalLink className="h-3 w-3" />
          </a>
          {issue.score > 0 ? (
            <span className="badge-trim tabular-nums rounded-md border border-[#137333]/20 bg-[#e6f4ea] px-2.5 py-0.5 text-xs font-medium text-[#137333]">
              매칭 {scorePercent}%
            </span>
          ) : null}
          {"isAnalyzed" in issue ? (
            <span className="badge-trim rounded-md border border-brand-500/20 bg-brand-50 px-2.5 py-0.5 text-xs font-medium text-brand-600">
              {getIssueAnalysisLabel(issue)}
            </span>
          ) : null}
        </div>

        <h1 className="text-xl font-medium leading-7 text-dark-text">{issue.title}</h1>

        {issue.labels.length > 0 ? (
          <div className="mt-4 flex flex-wrap gap-1.5">
            {issue.labels.map((label) => (
              <span
                className="badge-trim rounded border border-dark-border bg-dark-elevated px-2.5 py-0.5 text-xs text-dark-muted"
                key={label}
              >
                {label}
              </span>
            ))}
          </div>
        ) : null}

        {"reason" in issue && issue.reason ? (
          <div className="mt-5 rounded-lg border border-dark-border bg-dark-elevated p-4">
            <p className="text-xs font-semibold uppercase tracking-wider text-ink-muted">추천 이유</p>
            <p className="mt-1.5 text-sm leading-6 text-dark-text">{issue.reason}</p>
          </div>
        ) : null}
      </section>

      <section className="surface mb-4 p-6">
        <div className="mb-3 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-md border border-dark-border bg-dark-elevated text-dark-text">
              <Lightbulb className="h-4 w-4" />
            </span>
            <div>
              <h2 className="font-medium text-dark-text">1차 힌트 — 수정 가이드</h2>
              <p className="mt-1 text-xs leading-5 text-dark-muted">
                이슈 해결을 시작하기 전에 수정 방향과 주의 사항을 확인합니다.
              </p>
            </div>
          </div>
          {!guide ? (
            <button
              className="btn-secondary focus-ring shrink-0 px-4 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-50"
              disabled={guideLoading}
              onClick={handleGetGuide}
              type="button"
            >
              {guideLoading ? "분석 중..." : "힌트 보기"}
            </button>
          ) : null}
        </div>

        {guideError ? (
          <p className="text-sm font-medium text-[#c5221f]">{guideError}</p>
        ) : null}

        {guideLoading && !guide ? (
          <div className="mt-4 space-y-3 rounded-lg border border-dark-border bg-dark-elevated p-4">
            <div className="flex flex-wrap items-center justify-between gap-2 border-b border-dark-border pb-3">
              <div className="flex items-center gap-2">
                <span className="relative flex h-2.5 w-2.5">
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-brand-400 opacity-75" />
                  <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-brand-500" />
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
              <div className="flex items-center gap-1.5 text-[11px] font-medium text-dark-muted">
                <span className={`rounded px-2 py-0.5 ${streamStep === "ANALYZING" ? "bg-brand-50 text-brand-600 font-semibold" : "bg-white text-dark-muted"}`}>
                  1. 분석
                </span>
                <span className={`rounded px-2 py-0.5 ${streamStep === "FILES" ? "bg-brand-50 text-brand-600 font-semibold" : streamFilePaths.length > 0 ? "text-[#137333] font-semibold" : "bg-white text-dark-muted"}`}>
                  2. 파일
                </span>
                <span className={`rounded px-2 py-0.5 ${streamStep === "GUIDELINE" ? "bg-brand-50 text-brand-600 font-semibold" : streamGuideline ? "text-[#137333] font-semibold" : "bg-white text-dark-muted"}`}>
                  3. 가이드
                </span>
                <span className={`rounded px-2 py-0.5 ${streamStep === "SIDE_EFFECTS" ? "bg-brand-50 text-brand-600 font-semibold" : streamSideEffects.length > 0 ? "text-[#137333] font-semibold" : "bg-white text-dark-muted"}`}>
                  4. 주의점
                </span>
              </div>
            </div>

            {streamFilePaths.length > 0 ? (
              <div className="space-y-1">
                <p className="text-[11px] font-medium uppercase tracking-wide text-dark-muted">수정 대상 파일</p>
                <div className="flex flex-wrap gap-1.5">
                  {streamFilePaths.map((fp) => (
                    <code className="rounded border border-dark-border bg-white px-2 py-0.5 font-mono text-xs text-dark-text" key={fp}>
                      {fp}
                    </code>
                  ))}
                </div>
              </div>
            ) : null}

            {streamGuideline ? (
              <div className="rounded-md border border-dark-border bg-white p-3">
                <p className="mb-1 text-[11px] font-medium uppercase tracking-wide text-dark-muted">가이드라인</p>
                <p className="whitespace-pre-wrap text-xs leading-5 text-dark-text">
                  {streamGuideline}
                  <span className="ml-0.5 inline-block h-3.5 w-1 animate-pulse bg-brand-500 align-middle" />
                </p>
              </div>
            ) : null}

            {streamSideEffects.length > 0 ? (
              <div className="rounded-md border border-[#b06000]/20 bg-[#fef7e0] p-3">
                <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-[#b06000]">주의 사항</p>
                <ul className="list-disc space-y-1 pl-4 text-xs text-dark-text">
                  {streamSideEffects.map((se) => (
                    <li key={se}>{se}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : null}

        {guide ? (
          <div className="mt-4 space-y-4">
            <FilePathList filePaths={guide.filePaths} />
            <div className="rounded-lg border border-dark-border bg-dark-elevated p-4">
              <p className="mb-2 text-xs font-medium uppercase tracking-wide text-dark-muted">가이드라인</p>
              <p className="whitespace-pre-wrap text-sm leading-6 text-dark-text">
                {guide.guideline}
              </p>
            </div>
            {guide.sideEffects.length > 0 ? (
              <div className="rounded-lg border border-[#b06000]/20 bg-[#fef7e0] p-4">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-[#b06000]">주의 사항</p>
                <ul className="list-disc space-y-2 pl-4 text-sm leading-6 text-dark-text">
                  {guide.sideEffects.map((sideEffect) => (
                    <li key={sideEffect}>{sideEffect}</li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
        ) : null}
      </section>

      <section className="surface mb-5 p-6">
        <div className="mb-3 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-md border border-dark-border bg-dark-elevated text-dark-text">
              <Code2 className="h-4 w-4" />
            </span>
            <div>
              <h2 className="font-medium text-dark-text">2차 힌트 — 의사 코드</h2>
              <p className="mt-1 text-xs leading-5 text-dark-muted">
                구현에 들어가기 전에 구체적인 코드 수정 제안을 확인합니다.
              </p>
            </div>
          </div>
          {!pseudo ? (
            <button
              className="btn-secondary focus-ring shrink-0 px-4 py-2 text-xs disabled:cursor-not-allowed disabled:opacity-50"
              disabled={pseudoLoading}
              onClick={handleGetPseudo}
              type="button"
            >
              {pseudoLoading ? "분석 중..." : "코드 힌트 보기"}
            </button>
          ) : null}
        </div>

        {pseudoError ? (
          <p className="text-sm font-medium text-[#c5221f]">{pseudoError}</p>
        ) : null}

        {pseudo ? (
          <div className="mt-4 space-y-4">
            <FilePathList filePaths={pseudo.filePaths} />
            <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-lg border border-dark-border bg-dark-elevated p-4 font-mono text-xs leading-6 text-dark-text">
              {pseudo.pseudoCode}
            </pre>
          </div>
        ) : null}
      </section>

      <section className="surface p-6">
        <div className="flex items-center gap-3">
          <span className="grid h-8 w-8 shrink-0 place-items-center rounded-md border border-dark-border bg-dark-elevated text-dark-text">
            <GitPullRequest className="h-4 w-4" />
          </span>
          <h2 className="font-medium text-dark-text">PR 초안 생성</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-dark-muted">
          코드 작업이 완료되었다면 선택한 이슈의 맥락으로 PR 초안을 작성하세요.
        </p>
        <Link
          className="btn-primary focus-ring mt-5 inline-flex items-center gap-1.5 px-5 py-2.5 text-sm"
          href={prDraftHref}
        >
          <span>PR 초안 만들기 →</span>
        </Link>
      </section>
    </ProtectedLayout>
  );
}

function FilePathList({ filePaths }: { filePaths?: string[] | null }) {
  const safeFilePaths = normalizeStringArray(filePaths);

  return (
    <div>
      <p className="mb-2 text-xs font-medium uppercase tracking-wide text-dark-muted">수정 대상 파일</p>
      {safeFilePaths.length > 0 ? (
        <div className="space-y-1.5">
          {safeFilePaths.map((filePath) => (
            <code
              className="block rounded-md border border-dark-border bg-dark-elevated px-3 py-1.5 font-mono text-xs font-medium text-dark-text"
              key={filePath}
            >
              {filePath}
            </code>
          ))}
        </div>
      ) : (
        <p className="rounded-md border border-dark-border bg-dark-elevated px-3 py-2 text-xs text-dark-muted">
          수정 대상 파일 없음
        </p>
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
