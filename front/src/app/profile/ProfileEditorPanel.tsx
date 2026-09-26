"use client";

import { useState } from "react";
import { User, Mail, Sparkles, Save, RefreshCw, Loader2, CheckCircle2, AlertCircle } from "lucide-react";
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
  const vectorUpdatedAt = profile.profileReady && profile.vectorUpdatedAt ? profile.vectorUpdatedAt : null;
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
      <h1 className="mb-6 text-2xl font-bold text-dark-text">내 프로필</h1>

      {requestState.message ? (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm font-semibold text-emerald-400">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{requestState.message}</span>
        </div>
      ) : null}
      {requestState.error ? (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{requestState.error}</span>
        </div>
      ) : null}

      <section className="mb-6 rounded-xl border border-dark-border bg-dark-surface p-6 shadow-sm">
        <div className="mb-6 flex items-center gap-3.5">
          {profile.avatarUrl ? (
            <img
              alt={`${profile.login} GitHub avatar`}
              className="h-14 w-14 rounded-2xl border border-dark-border bg-dark-scaffold object-cover"
              referrerPolicy="no-referrer"
              src={profile.avatarUrl}
            />
          ) : (
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-500/20 text-xl font-bold text-brand-400 border border-brand-500/30">
              {getProfileInitial(profile)}
            </div>
          )}
          <div>
            <p className="text-lg font-bold text-dark-text">{profile.login ? `@${profile.login}` : profile.githubId}</p>
            <p className="text-xs font-mono text-dark-muted">
              {profile.login ? `GitHub ID ${profile.githubId}` : "GitHub ID"}
            </p>
          </div>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-dark-muted">이름</label>
            <div className="relative">
              <input
                className="w-full rounded-xl border border-dark-border bg-dark-scaffold px-3.5 py-2.5 text-sm text-dark-text placeholder:text-dark-muted transition-colors focus:border-brand-500 focus:outline-none"
                onChange={(event) => setName(event.target.value)}
                placeholder="이름을 입력하세요"
                type="text"
                value={name}
              />
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wide text-dark-muted">이메일</label>
            <div className="relative">
              <input
                className="w-full rounded-xl border border-dark-border bg-dark-scaffold px-3.5 py-2.5 text-sm text-dark-text placeholder:text-dark-muted transition-colors focus:border-brand-500 focus:outline-none"
                onChange={(event) => setEmail(event.target.value)}
                placeholder="이메일을 입력하세요"
                type="email"
                value={email}
              />
            </div>
          </div>

          <button
            className="focus-ring inline-flex items-center gap-2 rounded-xl bg-brand-500 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition-all hover:bg-brand-600 active:bg-brand-700 disabled:opacity-50"
            disabled={requestState.status !== "idle"}
            type="submit"
          >
            {requestState.status === "saving" ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>저장 중...</span>
              </>
            ) : (
              <>
                <Save className="h-4 w-4" />
                <span>프로필 저장</span>
              </>
            )}
          </button>
        </form>
      </section>

      <section className="rounded-xl border border-dark-border bg-dark-surface p-6 shadow-sm">
        <div className="flex items-center gap-2 mb-1">
          <Sparkles className="h-4 w-4 text-brand-400" />
          <h2 className="font-semibold text-dark-text">프로필 벡터</h2>
        </div>
        <p className="mb-4 text-sm text-dark-muted leading-relaxed">
          GitHub 활동을 분석하여 나에게 맞는 이슈를 추천받기 위한 임베딩 벡터를 생성합니다.
        </p>

        {vectorUpdatedAt ? (
          <p className="mb-4 text-xs font-mono text-dark-muted">
            마지막 업데이트: <span className="text-brand-400 font-semibold">{formatDateTime(vectorUpdatedAt)}</span>
          </p>
        ) : null}

        <div className="mb-5 rounded-xl border border-dark-border bg-dark-scaffold/60 p-4">
          <p className="mb-2.5 text-xs font-semibold uppercase tracking-wide text-dark-muted">감지된 주요 언어</p>
          {profile.primaryLanguages.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {profile.primaryLanguages.map((language) => (
                <span
                  className="rounded-full border border-brand-500/30 bg-brand-500/10 px-2.5 py-0.5 text-xs font-semibold text-brand-400"
                  key={language}
                >
                  {language}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-dark-muted">아직 분석된 주요 언어가 없습니다.</p>
          )}
        </div>

        <button
          className="focus-ring flex items-center gap-2 rounded-xl border border-brand-500/40 bg-brand-500/10 px-4 py-2.5 text-sm font-semibold text-brand-400 transition-colors hover:bg-brand-500/20 disabled:opacity-50"
          disabled={requestState.status !== "idle"}
          onClick={handleRefreshVector}
          type="button"
        >
          {requestState.status === "refreshing" ? (
            <>
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-brand-500 border-t-transparent" />
              <span>분석 중... (시간이 걸릴 수 있습니다)</span>
            </>
          ) : (
            <>
              <RefreshCw className="h-4 w-4" />
              <span>벡터 업데이트</span>
            </>
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
