"use client";

import { useState } from "react";
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
      setRequestState({ status: "idle", message: "프로필 벡터를 업데이트했습니다.", error: null });
    } catch {
      setRequestState({ status: "idle", message: null, error: "프로필 벡터 업데이트에 실패했습니다." });
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-bold text-github-text">내 프로필</h1>

      {requestState.message ? (
        <p className="mb-6 rounded-lg border border-green-500/30 bg-green-500/10 p-4 text-sm text-green-400">
          {requestState.message}
        </p>
      ) : null}
      {requestState.error ? (
        <p className="mb-6 rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-400">
          {requestState.error}
        </p>
      ) : null}

      <section className="mb-6 rounded-xl border border-github-border bg-github-surface p-6">
        <div className="mb-6 flex items-center gap-3">
          {profile.avatarUrl ? (
            <img
              alt={`${profile.login} GitHub avatar`}
              className="h-12 w-12 rounded-full border border-github-border bg-github-bg object-cover"
              referrerPolicy="no-referrer"
              src={profile.avatarUrl}
            />
          ) : (
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-github-purple/30 text-lg font-bold text-github-purple-light">
              {getProfileInitial(profile)}
            </div>
          )}
          <div>
            <p className="font-medium text-github-text">{profile.login ? `@${profile.login}` : profile.githubId}</p>
            <p className="text-xs text-github-muted">
              {profile.login ? `GitHub ID ${profile.githubId}` : "GitHub ID"}
            </p>
          </div>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="mb-1.5 block text-xs text-github-muted">이름</label>
            <input
              className="w-full rounded-lg border border-github-border bg-github-bg px-3 py-2 text-sm text-github-text transition-colors focus:border-github-purple focus:outline-none"
              onChange={(event) => setName(event.target.value)}
              placeholder="이름을 입력하세요"
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
              type="email"
              value={email}
            />
          </div>

          <button
            className="rounded-lg bg-github-purple px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-github-purple/90 disabled:opacity-50"
            disabled={requestState.status !== "idle"}
            type="submit"
          >
            {requestState.status === "saving" ? "저장 중..." : "프로필 저장"}
          </button>
        </form>
      </section>

      <section className="rounded-xl border border-github-border bg-github-surface p-6">
        <h2 className="mb-1 font-semibold text-github-text">프로필 벡터</h2>
        <p className="mb-4 text-sm text-github-muted">
          GitHub 활동을 분석하여 나에게 맞는 이슈를 추천받기 위한 임베딩 벡터를 생성합니다.
        </p>

        {profile.vectorUpdatedAt ? (
          <p className="mb-4 text-xs text-github-muted">
            마지막 업데이트: <span className="text-github-accent">{formatDateTime(profile.vectorUpdatedAt)}</span>
          </p>
        ) : null}

        <div className="mb-4">
          <p className="mb-2 text-xs text-github-muted">감지된 주요 언어</p>
          {profile.primaryLanguages.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {profile.primaryLanguages.map((language) => (
                <span
                  className="rounded-full border border-github-purple/30 bg-github-purple/20 px-2 py-0.5 text-xs text-github-purple-light"
                  key={language}
                >
                  {language}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-github-muted">아직 분석된 주요 언어가 없습니다.</p>
          )}
        </div>

        <button
          className="flex items-center gap-2 rounded-lg border border-github-accent/30 bg-github-accent/10 px-4 py-2 text-sm font-medium text-github-accent transition-colors hover:bg-github-accent/20 disabled:opacity-50"
          disabled={requestState.status !== "idle"}
          onClick={handleRefreshVector}
          type="button"
        >
          {requestState.status === "refreshing" ? (
            <>
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-github-accent border-t-transparent" />
              분석 중... (시간이 걸릴 수 있습니다)
            </>
          ) : (
            "벡터 업데이트"
          )}
        </button>
      </section>
    </div>
  );
}

function getProfileInitial(profile: UserProfile) {
  return (profile.name || profile.login || String(profile.githubId) || "?").at(0)?.toUpperCase() ?? "?";
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Seoul"
  }).format(new Date(value));
}
