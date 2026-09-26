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
      <section className="mb-8 space-y-3">
        <p className="text-xs font-semibold uppercase tracking-wider text-brand-400">Dashboard</p>
        <h1 className="text-2xl sm:text-3xl font-bold leading-tight text-dark-text">
          <span>안녕하세요,</span>{" "}
          <span className="inline-block max-w-full break-words align-bottom">{greetingTarget}</span>
        </h1>
        <p className="max-w-2xl text-base leading-7 text-dark-muted">
          aquila buddy — OpenSource contribution assistant 대시보드
        </p>
      </section>

      {error ? (
        <section className="surface mb-5 rounded-xl border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          사용자 정보를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </section>
      ) : null}

      {loading ? (
        <section className="surface rounded-xl p-6 text-sm font-semibold text-dark-muted">
          사용자 정보를 불러오는 중...
        </section>
      ) : null}

      {!loading && !hasVector ? (
        <section className="surface mb-6 rounded-xl border-amber-500/40 bg-amber-500/10 p-5">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3">
              <AlertCircle className="h-5 w-5 text-amber-400 shrink-0 mt-0.5" />
              <div>
                <p className="text-base font-bold text-amber-300">프로필 분석이 필요합니다</p>
                <p className="mt-1 text-sm leading-6 text-dark-muted">
                  이슈 추천을 받으려면 먼저 마이페이지에서 벡터 업데이트를 완료해주세요.
                </p>
              </div>
            </div>
            <Link
              className="focus-ring inline-flex items-center justify-center rounded-xl bg-brand-500 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-brand-600 active:bg-brand-700 transition-all shrink-0"
              href="/profile"
            >
              프로필로 이동
            </Link>
          </div>
        </section>
      ) : null}

      {user ? (
        <section className="surface rounded-xl p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wider text-dark-muted">내 계정 정보</p>
              <h2 className="mt-1 text-xl font-bold text-dark-text">{displayName || "GitHub 사용자"}</h2>
            </div>
            <Link className="focus-ring inline-flex items-center gap-1 text-sm font-semibold text-brand-400 hover:text-brand-300" href="/profile">
              <span>프로필 편집</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>

          <dl className="mt-6 grid gap-6 md:grid-cols-2">
            <div className="rounded-lg bg-dark-scaffold/60 p-4 border border-dark-border">
              <dt className="text-xs font-semibold uppercase tracking-wide text-dark-muted">GitHub ID</dt>
              <dd className="mt-1 break-all text-sm font-semibold text-dark-text">
                {user.login || user.githubId}
              </dd>
            </div>
            <div className="rounded-lg bg-dark-scaffold/60 p-4 border border-dark-border">
              <dt className="text-xs font-semibold uppercase tracking-wide text-dark-muted">Email</dt>
              <dd className="mt-1 break-all text-sm font-semibold text-dark-text">
                {user.email || "등록된 이메일 없음"}
              </dd>
            </div>
            <div className="rounded-lg bg-dark-scaffold/60 p-4 border border-dark-border">
              <dt className="text-xs font-semibold uppercase tracking-wide text-dark-muted">주요 언어</dt>
              <dd className="mt-2 flex flex-wrap gap-2">
                {user.primaryLanguages.length > 0 ? (
                  user.primaryLanguages.map((language) => (
                    <span
                      className="rounded-full border border-brand-500/30 bg-brand-500/10 px-2.5 py-0.5 text-xs font-semibold text-brand-400"
                      key={language}
                    >
                      {language}
                    </span>
                  ))
                ) : (
                  <span className="text-sm font-semibold text-dark-muted">분석된 언어 없음</span>
                )}
              </dd>
            </div>
            <div className="rounded-lg bg-dark-scaffold/60 p-4 border border-dark-border">
              <dt className="text-xs font-semibold uppercase tracking-wide text-dark-muted">벡터 업데이트</dt>
              <dd className="mt-1 text-sm font-semibold text-dark-text">
                {vectorUpdatedAt ? formatDate(vectorUpdatedAt) : "업데이트 전"}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}

      <section className="mt-8 grid gap-5 md:grid-cols-3">
        {serviceSteps.map((step) => {
          const Icon = step.icon;
          return (
            <Link
              className={`focus-ring surface block rounded-xl p-6 transition-all duration-200 hover:shadow-lg hover:shadow-brand-500/10 ${step.accentClass}`}
              href={step.href}
              key={step.step}
            >
              <div className="flex items-center justify-between">
                <p className="text-xs font-bold uppercase tracking-wider text-brand-400">{step.step}</p>
                <span className="grid h-8 w-8 place-items-center rounded-lg bg-brand-500/10 text-brand-400">
                  <Icon className="h-4 w-4" />
                </span>
              </div>
              <h3 className="mt-3 text-lg font-bold text-dark-text">{step.title}</h3>
              <p className="mt-2 text-sm leading-6 text-dark-muted">{step.description}</p>
              <span className="mt-5 inline-flex items-center gap-1.5 text-sm font-semibold text-brand-400 hover:text-brand-300">
                <span>{step.action}</span>
                <ArrowRight className="h-4 w-4" />
              </span>
            </Link>
          );
        })}
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
    icon: Compass,
    accentClass: "hover:border-brand-500 hover:bg-brand-500/5"
  },
  {
    step: "STEP 2",
    title: "이슈 추천",
    description: "나의 기술 스택에 맞는 오픈소스 이슈를 추천받고 분석 힌트를 확인합니다.",
    action: "이슈 추천 보기",
    href: "/issues",
    icon: Sparkles,
    accentClass: "hover:border-brand-500 hover:bg-brand-500/5"
  },
  {
    step: "STEP 3",
    title: "PR 초안 생성",
    description: "코드 작업 후 브랜치 정보를 입력하면 AI가 PR을 자동 작성합니다.",
    action: "초안 만들기",
    href: "/pr-draft",
    icon: GitPullRequest,
    accentClass: "hover:border-brand-500 hover:bg-brand-500/5"
  }
];
