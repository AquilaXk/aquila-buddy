"use client";

import { useEffect, useState } from "react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { getUserProfile, type UserProfile } from "@/lib/api";
import { ProfileEditorPanel } from "./ProfileEditorPanel";

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [retryCount, setRetryCount] = useState(0);

  useEffect(() => {
    let isMounted = true;
    setLoading(true);

    getUserProfile()
      .then((loadedProfile) => {
        if (!isMounted) {
          return;
        }

        setProfile(loadedProfile);
        setError(null);
      })
      .catch((caught) => {
        if (!isMounted) {
          return;
        }

        setProfile(null);
        setError(caught instanceof Error ? caught.message : "알 수 없는 오류가 발생했습니다.");
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [retryCount]);

  return (
    <ProtectedLayout>
      {loading ? (
        <section className="primer-box p-6 text-[13.5px] font-medium text-[#59636e]">
          프로필을 불러오는 중...
        </section>
      ) : null}

      {!loading && error ? (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-[#ff8182]/40 bg-[#ffebe9] p-4 text-[13.5px] font-medium text-[#d1242f]">
          <span>프로필을 불러오지 못했습니다: {error}</span>
          <button
            className="btn-secondary focus-ring px-3 py-1.5 text-xs"
            onClick={() => setRetryCount((c) => c + 1)}
            type="button"
          >
            다시 시도
          </button>
        </div>
      ) : null}

      {!loading && profile ? (
        <ProfileEditorPanel initialProfile={profile} />
      ) : null}

      {!loading && !error && !profile ? (
        <section className="primer-box p-6">
          <h2 className="text-[16px] font-semibold text-[#1f2328]">내 프로필</h2>
          <p className="mt-2 text-[13.5px] leading-[1.55] text-[#59636e]">프로필 데이터가 준비되지 않았습니다.</p>
        </section>
      ) : null}
    </ProtectedLayout>
  );
}
