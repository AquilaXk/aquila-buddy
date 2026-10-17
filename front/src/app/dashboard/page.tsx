"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  Compass,
  Sparkles,
  GitPullRequest,
  ArrowRight,
  AlertCircle
} from "lucide-react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { getUserProfile, type UserProfile } from "@/lib/api";
import { formatDate } from "@/lib/format";

export default function DashboardPage() {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

    getUserProfile()
      .then((profile) => {
        if (!isMounted) {
          return;
        }

        setUser(profile);
        setError(null);
      })
      .catch((caught) => {
        if (!isMounted) {
          return;
        }

        setError(caught instanceof Error ? caught.message : "사용자 정보를 불러오지 못했습니다.");
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

  const displayName = user?.name || user?.login || (user?.githubId ? String(user.githubId) : "");
  const greetingTarget = loading ? "..." : displayName ? `${displayName}님` : "사용자님";
  const vectorUpdatedAt = user?.vectorUpdatedAt ?? null;
  const hasVector = Boolean(user?.profileReady || user?.vectorUpdatedAt);

  return (
    <ProtectedLayout>
      <section className="mb-6 space-y-1.5">
        <p className="text-xs font-medium uppercase tracking-wider text-ink-muted">기여 워크스페이스 개요</p>
        <h1 className="text-2xl font-medium leading-tight text-dark-text">
          <span>안녕하세요,</span>{" "}
          <span className="inline-block max-w-full break-words align-bottom">{greetingTarget}</span>
        </h1>
      </section>

      {error ? (
        <section className="mb-5 flex items-center gap-2.5 rounded-lg border border-[#c5221f]/20 bg-[#fce8e6] p-4 text-sm font-medium text-[#c5221f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>사용자 정보를 불러오지 못했습니다: {error}</span>
        </section>
      ) : null}

      {loading ? (
        <section className="surface p-6 text-sm font-medium text-dark-muted">
          사용자 정보를 불러오는 중...
        </section>
      ) : null}

      {!loading && !hasVector ? (
        <section className="mb-6 rounded-lg border border-[#b06000]/25 bg-[#fef7e0] p-4">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3">
              <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-[#b06000]" />
              <div>
                <p className="text-sm font-semibold text-[#b06000]">프로필 분석이 필요합니다</p>
                <p className="mt-0.5 text-xs leading-5 text-dark-muted">
                  이슈 추천을 받으려면 먼저 마이페이지에서 벡터 업데이트를 완료해주세요.
                </p>
              </div>
            </div>
            <Link
              className="btn-primary focus-ring inline-flex shrink-0 items-center justify-center px-4 py-2 text-xs"
              href="/profile"
            >
              프로필로 이동
            </Link>
          </div>
        </section>
      ) : null}

      {user ? (
        <section className="surface mb-6 p-6">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-xs font-medium uppercase tracking-wider text-ink-muted">내 계정 정보</p>
              <h2 className="mt-1 text-lg font-medium text-dark-text">{displayName || "GitHub 사용자"}</h2>
            </div>
            <Link
              className="focus-ring inline-flex items-center gap-1 text-xs font-medium text-brand-500 hover:text-brand-600"
              href="/profile"
            >
              <span>프로필 편집</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          <dl className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <div className="rounded-md border border-dark-border bg-dark-elevated p-3.5">
              <dt className="text-[11px] font-medium uppercase tracking-wide text-ink-muted">GitHub ID</dt>
              <dd className="mt-1 break-all font-mono text-xs font-medium text-dark-text">
                {user.login || user.githubId}
              </dd>
            </div>
            <div className="rounded-md border border-dark-border bg-dark-elevated p-3.5">
              <dt className="text-[11px] font-medium uppercase tracking-wide text-ink-muted">Email</dt>
              <dd className="mt-1 break-all text-xs font-medium text-dark-text">
                {user.email || "등록된 이메일 없음"}
              </dd>
            </div>
            <div className="rounded-md border border-dark-border bg-dark-elevated p-3.5">
              <dt className="text-[11px] font-medium uppercase tracking-wide text-ink-muted">주요 언어</dt>
              <dd className="mt-1.5 flex flex-wrap gap-1.5">
                {user.primaryLanguages.length > 0 ? (
                  user.primaryLanguages.map((language) => (
                    <span
                      className="badge-trim rounded border border-dark-border bg-white px-2 py-0.5 text-[11px] font-medium text-dark-text"
                      key={language}
                    >
                      {language}
                    </span>
                  ))
                ) : (
                  <span className="text-xs text-dark-muted">분석된 언어 없음</span>
                )}
              </dd>
            </div>
            <div className="rounded-md border border-dark-border bg-dark-elevated p-3.5">
              <dt className="text-[11px] font-medium uppercase tracking-wide text-ink-muted">벡터 업데이트</dt>
              <dd className="tabular-nums mt-1 text-xs font-medium text-dark-text">
                {vectorUpdatedAt ? formatDate(vectorUpdatedAt) : "업데이트 전"}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      <section className="surface divide-y divide-dark-border overflow-hidden">
        <div className="bg-dark-elevated px-5 py-3">
          <h2 className="text-xs font-semibold uppercase tracking-wider text-ink-muted">빠른 작업</h2>
        </div>
        {quickActions.map((item) => {
          const Icon = item.icon;
          return (
            <Link
              className="focus-ring flex items-center justify-between gap-4 px-5 py-4 transition-colors hover:bg-dark-elevated"
              href={item.href}
              key={item.href}
            >
              <div className="flex min-w-0 items-center gap-3.5">
                <span className="grid h-8 w-8 shrink-0 place-items-center rounded-md border border-dark-border bg-dark-elevated text-dark-text">
                  <Icon className="h-4 w-4" />
                </span>
                <div className="min-w-0">
                  <h3 className="text-sm font-medium text-dark-text">{item.title}</h3>
                  <p className="mt-0.5 text-xs text-dark-muted">{item.description}</p>
                </div>
              </div>
              <span className="inline-flex shrink-0 items-center gap-1 text-xs font-medium text-brand-500">
                <span>{item.action}</span>
                <ArrowRight className="h-3.5 w-3.5" />
              </span>
            </Link>
          );
        })}
      </section>
    </ProtectedLayout>
  );
}

const quickActions = [
  {
    title: "프로필 분석",
    description: "GitHub 활동과 관심 기술 토픽을 기반으로 추천 임베딩 벡터를 갱신합니다.",
    action: "프로필로 이동",
    href: "/profile",
    icon: Compass
  },
  {
    title: "이슈 추천",
    description: "기술 스택과 매칭되는 오픈소스 이슈를 탐색하고 코드 분석 가이드를 확인합니다.",
    action: "이슈 추천 보기",
    href: "/issues",
    icon: Sparkles
  },
  {
    title: "PR 초안 생성",
    description: "작업 브랜치와 이슈 맥락을 연결하여 PR 제목·본문 초안과 준비도를 점검합니다.",
    action: "초안 만들기",
    href: "/pr-draft",
    icon: GitPullRequest
  }
];
