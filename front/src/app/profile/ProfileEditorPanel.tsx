"use client";

import { useMemo, useState } from "react";
import {
  refreshUserProfileVector,
  updateUserProfile,
  type UpdateUserProfileRequest,
  type UserProfile
} from "@/lib/api";

type ProfileEditorPanelProps = {
  initialProfile: UserProfile;
};

type RequestState = {
  status: "idle" | "saving" | "refreshing";
  message: string | null;
  error: string | null;
};

export function ProfileEditorPanel({ initialProfile }: ProfileEditorPanelProps) {
  const [profile, setProfile] = useState(initialProfile);
  const [name, setName] = useState(initialProfile.name);
  const [email, setEmail] = useState(initialProfile.email);
  const [requestState, setRequestState] = useState<RequestState>({
    status: "idle",
    message: null,
    error: null
  });

  const vectorLabel = useMemo(() => {
    return profile.profileReady ? "Ready" : "Pending";
  }, [profile.profileReady]);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setRequestState({ status: "saving", message: null, error: null });

    try {
      const request: UpdateUserProfileRequest = {
        name,
        email,
        primaryLanguages: profile.primaryLanguages,
        topics: profile.topics
      };
      const updated = await updateUserProfile(request);
      setProfile(updated);
      setRequestState({ status: "idle", message: "프로필을 저장했습니다.", error: null });
    } catch {
      setRequestState({ status: "idle", message: null, error: "프로필 저장에 실패했습니다." });
    }
  }

  async function handleRefreshVector() {
    setRequestState({ status: "refreshing", message: null, error: null });

    try {
      const refreshed = await refreshUserProfileVector();
      setProfile(refreshed);
      setRequestState({ status: "idle", message: "Profile vector를 갱신했습니다.", error: null });
    } catch {
      setRequestState({ status: "idle", message: null, error: "Profile vector 갱신에 실패했습니다." });
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-github-text">내 프로필</h1>

      <section className="mb-6 rounded-xl border border-github-border bg-github-surface p-6">
        <div className="mb-6 flex items-center gap-3">
          <div className="flex h-12 w-12 items-center justify-center rounded-full bg-github-purple/30 text-lg font-bold text-github-purple-light">
            {getProfileInitial(profile)}
          </div>
          <div>
            <p className="font-medium text-github-text">{profile.githubId}</p>
            <p className="text-xs text-github-muted">GitHub ID</p>
          </div>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="mb-1.5 block text-xs text-github-muted">이름</label>
            <input
              className="w-full rounded-lg border border-github-border bg-github-bg px-3 py-2 text-sm text-github-text transition-colors focus:border-github-purple focus:outline-none"
              onChange={(event) => setName(event.target.value)}
              placeholder="이름을 입력하세요"
              required
              type="text"
              value={name}
            />
          </div>

          <div>
            <label className="mb-1.5 block text-xs text-github-muted">이메일</label>
            <input
              className="w-full rounded-lg border border-github-border bg-github-bg px-3 py-2 text-sm text-github-text transition-colors focus:border-github-purple focus:outline-none"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="이메일을 입력하세요"
              required
              type="email"
              value={email}
            />
          </div>

          {requestState.message ? (
            <p className="rounded-lg border border-green-500/30 bg-green-500/10 p-4 text-sm text-green-400">
              {requestState.message}
            </p>
          ) : null}
          {requestState.error ? (
            <p className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-400">
              {requestState.error}
            </p>
          ) : null}

          <button
            className="rounded-lg bg-github-purple px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-github-purple/90 disabled:opacity-50"
            disabled={requestState.status !== "idle"}
            type="submit"
          >
            {requestState.status === "saving" ? "저장 중..." : "프로필 저장"}
          </button>
        </form>
      </section>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="surface p-4">
          <h3 className="text-sm font-semibold">Profile Status</h3>
          <p className="mt-2 text-lg font-bold text-github-text">{vectorLabel}</p>
          <p className="mt-2 text-sm leading-6 text-github-muted">
            마지막 업데이트 {formatDateTime(profile.vectorUpdatedAt)}
          </p>
        </div>

        <TokenList title="Languages" items={profile.primaryLanguages} />
        <TokenList title="Topics" items={profile.topics} />
      </div>
    </div>
  );
}

function getProfileInitial(profile: UserProfile) {
  return (profile.name || profile.login || String(profile.githubId) || "?").at(0)?.toUpperCase() ?? "?";
}

function TokenList({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="surface p-4">
      <h3 className="text-sm font-semibold">{title}</h3>
      <div className="mt-3 flex flex-wrap gap-2">
        {items.map((item) => (
          <span key={item} className="rounded-md bg-github-bg px-2 py-1 text-xs font-semibold text-github-muted">
            {item}
          </span>
        ))}
      </div>
    </div>
  );
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Seoul"
  }).format(new Date(value));
}
