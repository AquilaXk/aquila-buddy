"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";
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
                GitHub 활동과 기술 스택을 분석하면 이슈 추천과 PR 초안 품질이 높아집니다.
              </p>
            </div>
            <Link
              className="focus-ring inline-flex items-center justify-center rounded-md border border-yellow-400/60 px-4 py-2 text-sm font-semibold text-yellow-200 hover:bg-yellow-500/10"
              href="/profile"
            >
              프로필 편집
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
                {user.vectorUpdatedAt ? formatDate(user.vectorUpdatedAt) : "업데이트 전"}
              </dd>
            </div>
          </dl>
        </section>
      ) : null}
    </ProtectedLayout>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
