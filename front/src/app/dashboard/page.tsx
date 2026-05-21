"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import ProtectedLayout from "@/components/ProtectedLayout";
import { getUserProfile, type UserProfile } from "@/lib/api";

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
  const vectorUpdatedAt = user?.vectorUpdatedAt ?? null;
  const hasVector = Boolean(user?.profileReady || user?.vectorUpdatedAt);

  return (
    <ProtectedLayout>
      <section className="mb-8 space-y-3">
        <p className="text-sm font-semibold uppercase tracking-wide text-github-muted">Dashboard</p>
        <h1 className="text-3xl font-bold leading-tight text-github-text">
          안녕하세요, {loading ? "..." : displayName ? `${displayName}님` : "사용자님"}
        </h1>
        <p className="max-w-2xl text-base leading-7 text-github-muted">
          aquila buddy — OpenSource contribution assistant 대시보드
        </p>
      </section>

      {error ? (
        <section className="surface mb-5 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          사용자 정보를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </section>
      ) : null}

      {loading ? (
        <section className="surface p-6 text-sm font-semibold text-github-muted">
          사용자 정보를 불러오는 중...
        </section>
      ) : null}

      {!loading && !hasVector ? (
        <section className="surface mb-5 border-yellow-500/30 bg-yellow-500/10 p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="text-base font-bold text-yellow-300">프로필 분석이 필요합니다</p>
              <p className="mt-2 text-sm leading-6 text-github-muted">
                이슈 추천을 받으려면 먼저 마이페이지에서 벡터 업데이트를 완료해주세요.
              </p>
            </div>
            <Link
              className="focus-ring inline-flex items-center justify-center rounded-md border border-yellow-400/60 px-4 py-2 text-sm font-semibold text-yellow-200 hover:bg-yellow-500/10"
              href="/profile"
            >
              프로필로 이동
            </Link>
          </div>
        </section>
      ) : null}

      {user ? (
        <section className="surface p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-sm font-semibold text-github-muted">내 계정 정보</p>
              <h2 className="mt-2 text-xl font-bold text-github-text">{displayName || "GitHub 사용자"}</h2>
            </div>
            <Link className="focus-ring text-sm font-semibold text-github-accent" href="/profile">
              프로필 편집
            </Link>
          </div>

          <dl className="mt-5 grid gap-4 md:grid-cols-2">
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-github-muted">GitHub ID</dt>
              <dd className="mt-1 break-all text-sm font-semibold text-github-text">
                {user.login || user.githubId}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-github-muted">Email</dt>
              <dd className="mt-1 break-all text-sm font-semibold text-github-text">
                {user.email || "등록된 이메일 없음"}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-github-muted">주요 언어</dt>
              <dd className="mt-2 flex flex-wrap gap-2">
                {user.primaryLanguages.length > 0 ? (
                  user.primaryLanguages.map((language) => (
                    <span
                      className="rounded-md border border-github-border px-2 py-1 text-xs font-semibold text-github-muted"
                      key={language}
                    >
                      {language}
                    </span>
                  ))
                ) : (
                  <span className="text-sm font-semibold text-github-muted">분석된 언어 없음</span>
                )}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-semibold uppercase tracking-wide text-github-muted">벡터 업데이트</dt>
              <dd className="mt-1 text-sm font-semibold text-github-text">
                {vectorUpdatedAt ? formatDate(vectorUpdatedAt) : "업데이트 전"}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      <section className="mt-6 grid gap-4 md:grid-cols-3">
        {serviceSteps.map((step) => (
          <Link
            className={`focus-ring surface block p-5 transition hover:border-github-accent ${step.accentClass}`}
            href={step.href}
            key={step.step}
          >
            <p className="text-xs font-bold uppercase tracking-wide text-github-muted">{step.step}</p>
            <h3 className="mt-3 text-lg font-bold text-github-text">{step.title}</h3>
            <p className="mt-3 text-sm leading-6 text-github-muted">{step.description}</p>
            <span className="mt-5 inline-flex text-sm font-semibold text-github-accent">
              {step.action}
            </span>
          </Link>
        ))}
      </section>
    </ProtectedLayout>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

const serviceSteps = [
  {
    step: "STEP 1",
    title: "프로필 분석",
    description: "GitHub 활동을 분석하여 AI 추천을 위한 벡터를 생성합니다.",
    action: "프로필로 이동",
    href: "/profile",
    accentClass: "hover:bg-github-accent/5"
  },
  {
    step: "STEP 2",
    title: "이슈 추천",
    description: "나의 기술 스택에 맞는 오픈소스 이슈를 추천받고 분석 힌트를 확인합니다.",
    action: "이슈 추천 보기",
    href: "/issues",
    accentClass: "hover:bg-emerald-500/5"
  },
  {
    step: "STEP 3",
    title: "PR 초안 생성",
    description: "코드 작업 후 브랜치 정보를 입력하면 AI가 PR을 자동 작성합니다.",
    action: "초안 만들기",
    href: "/pr-draft",
    accentClass: "hover:bg-yellow-500/5"
  }
];
