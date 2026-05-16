"use client";

import { useEffect, useState } from "react";
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
    </ProtectedLayout>
  );
}
