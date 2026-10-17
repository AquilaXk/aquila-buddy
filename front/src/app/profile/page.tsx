"use client";

import { useEffect, useState } from "react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { getUserProfile, type UserProfile } from "@/lib/api";
import { ProfileEditorPanel } from "./ProfileEditorPanel";

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let isMounted = true;

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
  }, []);

  return (
    <ProtectedLayout>
      {loading ? (
        <section className="surface p-6 text-sm font-medium text-dark-muted">
          프로필을 불러오는 중...
        </section>
      ) : null}

      {!loading && error ? (
        <div className="mb-4 rounded-lg border border-[#c5221f]/20 bg-[#fce8e6] p-4 text-sm font-medium text-[#c5221f]">
          프로필을 불러오지 못했습니다: {error}
        </div>
      ) : null}

      {!loading && profile ? (
        <ProfileEditorPanel initialProfile={profile} />
      ) : null}

      {!loading && !profile ? (
        <section className="surface p-6">
          <h2 className="text-lg font-medium text-dark-text">내 프로필</h2>
          <p className="mt-2 text-sm leading-6 text-dark-muted">프로필 데이터가 준비되지 않았습니다.</p>
        </section>
      ) : null}
    </ProtectedLayout>
  );
}
