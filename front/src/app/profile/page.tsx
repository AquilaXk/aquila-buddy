"use client";

import { useEffect, useState } from "react";
import ProtectedLayout from "@/components/ProtectedLayout";
import { getUserProfile, type UserProfile } from "@/lib/api";
import { ProfileEditorPanel } from "./ProfileEditorPanel";

export default function ProfilePage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);

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
        setError(caught);
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
        <section className="surface rounded-xl p-6 text-sm font-semibold text-dark-muted">
          프로필을 불러오는 중...
        </section>
      ) : null}

      {!loading && error ? (
        <div className="surface mb-4 rounded-xl border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          프로필을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}

      {!loading && profile ? (
        <ProfileEditorPanel initialProfile={profile} />
      ) : null}

      {!loading && !profile ? (
        <section className="surface rounded-xl p-6">
          <h2 className="text-lg font-bold text-dark-text">Profile</h2>
          <p className="mt-2 text-sm leading-6 text-dark-muted">프로필 데이터가 준비되지 않았습니다.</p>
        </section>
      ) : null}
    </ProtectedLayout>
  );
}
