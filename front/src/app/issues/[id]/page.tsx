"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ExternalLink, Lightbulb, Code2, GitPullRequest } from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import {
  getIssueDetail,
  getIssueGuide,
  getIssuePseudoCode,
  getRecommendedIssueHistory,
  getRecommendedIssues,
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

    try {
      setGuide(normalizeIssueGuide(await getIssueGuide(issueId)));
    } catch (caught) {
      setGuideError(caught instanceof Error ? caught.message : "가이드를 불러오지 못했습니다.");
    } finally {
      setGuideLoading(false);
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
          <div className="surface rounded-xl h-24 animate-pulse" />
          <div className="surface rounded-xl h-32 animate-pulse" />
          <div className="surface rounded-xl h-32 animate-pulse" />
        </div>
      </ProtectedLayout>
    );
  }

  if (notFound || !issue) {
    return (
      <ProtectedLayout>
        <section className="surface rounded-xl py-20 text-center">
          <h1 className="text-2xl font-bold text-dark-text">이슈를 찾을 수 없습니다.</h1>
          <p className="mt-3 text-sm font-semibold text-dark-muted">
            추천 이력에서 선택한 이슈 정보를 확인하지 못했습니다.
          </p>
          <Link
            className="focus-ring mt-6 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-400 hover:text-brand-300"
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
  const prDraftHref = buildPullRequestDraftHref(issue, guide);

  return (
    <ProtectedLayout>
      <div className="mb-6">
        <Link
          className="focus-ring inline-flex items-center gap-1.5 text-sm font-semibold text-dark-muted hover:text-dark-text transition-colors"
          href="/issues"
        >
          <ArrowLeft className="h-4 w-4" />
          <span>← 추천 이슈 목록</span>
        </Link>
      </div>

      <section className="surface mb-5 rounded-xl p-6">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <StatusBadge status={issue.status} />
          <a
            className="inline-flex items-center gap-1 text-xs font-mono text-dark-muted hover:text-brand-400 transition-colors"
            href={githubUrl}
            rel="noopener noreferrer"
            target="_blank"
          >
            <span>{issue.repoFullName} #{issue.issueNumber}</span>
            <ExternalLink className="h-3 w-3" />
            <span>↗</span>
          </a>
          {issue.score > 0 ? (
            <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2.5 py-0.5 text-xs font-semibold text-emerald-400">
              매칭 {scorePercent}%
            </span>
          ) : null}
          {"isAnalyzed" in issue ? (
            <span className="rounded-full border border-brand-500/30 bg-brand-500/10 px-2.5 py-0.5 text-xs font-semibold text-brand-400">
              {getIssueAnalysisLabel(issue)}
            </span>
          ) : null}
        </div>

        <h1 className="text-xl font-bold leading-7 text-dark-text">{issue.title}</h1>

        {issue.labels.length > 0 ? (
          <div className="mt-4 flex flex-wrap gap-2">
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

        {"reason" in issue && issue.reason ? (
          <div className="mt-5 rounded-xl border border-dark-border bg-dark-scaffold/70 p-4">
            <p className="text-xs font-semibold uppercase tracking-wider text-brand-400">추천 이유</p>
            <p className="mt-2 text-sm leading-6 text-dark-text">{issue.reason}</p>
          </div>
        ) : null}
      </section>

      <section className="surface mb-4 rounded-xl p-6">
        <div className="mb-3 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500/10 text-brand-400 shrink-0 mt-0.5">
              <Lightbulb className="h-4 w-4" />
            </span>
            <div>
              <h2 className="font-semibold text-dark-text">1차 힌트 — 수정 가이드</h2>
              <p className="mt-1 text-xs leading-5 text-dark-muted">
                이슈 해결을 시작하기 전에 수정 방향과 주의 사항을 확인합니다.
              </p>
            </div>
          </div>
          {!guide ? (
            <button
              className="focus-ring shrink-0 rounded-xl bg-brand-500/20 px-4 py-2 text-sm font-semibold text-brand-400 hover:bg-brand-500/30 transition-colors disabled:cursor-not-allowed disabled:opacity-50"
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
          <div className="mt-4 space-y-4">
            <FilePathList filePaths={guide.filePaths} />
            <div className="rounded-xl border border-dark-border bg-dark-scaffold/60 p-4">
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-dark-muted">가이드라인</p>
              <p className="whitespace-pre-wrap text-sm leading-6 text-dark-text">
                {guide.guideline}
              </p>
            </div>
            {guide.sideEffects.length > 0 ? (
              <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 p-4">
                <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-amber-300">주의 사항</p>
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

      <section className="surface mb-5 rounded-xl p-6">
        <div className="mb-3 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500/10 text-brand-400 shrink-0 mt-0.5">
              <Code2 className="h-4 w-4" />
            </span>
            <div>
              <h2 className="font-semibold text-dark-text">2차 힌트 — 의사 코드</h2>
              <p className="mt-1 text-xs leading-5 text-dark-muted">
                구현에 들어가기 전에 구체적인 코드 수정 제안을 확인합니다.
              </p>
            </div>
          </div>
          {!pseudo ? (
            <button
              className="focus-ring shrink-0 rounded-xl border border-brand-500/40 bg-brand-500/10 px-4 py-2 text-sm font-semibold text-brand-400 hover:bg-brand-500/20 transition-colors disabled:cursor-not-allowed disabled:opacity-50"
              disabled={pseudoLoading}
              onClick={handleGetPseudo}
              type="button"
            >
              {pseudoLoading ? "분석 중..." : "코드 힌트 보기"}
            </button>
          ) : null}
        </div>

        {pseudoError ? (
          <p className="text-sm font-semibold text-red-400">{pseudoError}</p>
        ) : null}

        {pseudo ? (
          <div className="mt-4 space-y-4">
            <FilePathList filePaths={pseudo.filePaths} />
            <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-xl border border-dark-border bg-dark-scaffold p-4 font-mono text-xs leading-6 text-dark-text">
              {pseudo.pseudoCode}
            </pre>
          </div>
        ) : null}
      </section>

      <section className="surface rounded-xl border-brand-500/40 p-6 shadow-md shadow-brand-500/5">
        <div className="flex items-center gap-3">
          <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500/10 text-brand-400 shrink-0">
            <GitPullRequest className="h-4 w-4" />
          </span>
          <h2 className="font-semibold text-dark-text">PR 초안 생성</h2>
        </div>
        <p className="mt-2 text-sm leading-6 text-dark-muted">
          코드 작업이 완료되었다면 선택한 이슈의 맥락으로 PR 초안을 작성하세요.
        </p>
        <Link
          className="focus-ring mt-5 inline-flex items-center gap-1.5 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 active:bg-brand-700 transition-all"
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
      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-dark-muted">수정 대상 파일</p>
      {safeFilePaths.length > 0 ? (
        <div className="space-y-1.5">
          {safeFilePaths.map((filePath) => (
            <code
              className="block rounded-lg border border-dark-border bg-dark-scaffold px-3 py-1.5 font-mono text-xs font-semibold text-brand-400"
              key={filePath}
            >
              {filePath}
            </code>
          ))}
        </div>
      ) : (
        <p className="rounded-lg border border-dark-border bg-dark-scaffold px-3 py-2 text-xs font-semibold text-dark-muted">
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

function StatusBadge({ status }: { status: RecommendedIssue["status"] }) {
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

function getIssueAnalysisLabel(issue: RecommendedIssueHistory) {
  return issue.isAnalyzed ? "분석 완료" : "분석 전";
}

function buildPullRequestDraftHref(issue: IssueDetailIssue, guide: IssueGuide | null) {
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
