"use client";

import { useState } from "react";
import { Sparkles, Save, RefreshCw, Loader2, CheckCircle2, AlertCircle, Plus, X } from "lucide-react";
import {
  refreshUserProfileVector,
  updateUserProfile,
  type UpdateUserProfileRequest,
  type UserProfile
} from "@/lib/api";
import { formatDate } from "@/lib/format";

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
  const [topics, setTopics] = useState<string[]>(initialProfile.topics ?? []);
  const [topicInput, setTopicInput] = useState("");
  const vectorUpdatedAt = profile.profileReady && profile.vectorUpdatedAt ? profile.vectorUpdatedAt : null;
  const [requestState, setRequestState] = useState<RequestState>({
    status: "idle",
    message: null,
    error: null
  });

  function handleAddTopic() {
    const normalized = topicInput.trim().toLowerCase();
    if (!normalized) {
      return;
    }
    if (!topics.includes(normalized)) {
      setTopics((prev) => [...prev, normalized]);
    }
    setTopicInput("");
  }

  function handleRemoveTopic(target: string) {
    setTopics((prev) => prev.filter((topic) => topic !== target));
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setRequestState({ status: "saving", message: null, error: null });

    try {
      const request: UpdateUserProfileRequest = {
        name,
        email,
        primaryLanguages: profile.primaryLanguages,
        topics
      };
      const updated = await updateUserProfile(request);
      setProfile(updated);
      setTopics(updated.topics ?? topics);
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
      setTopics(refreshed.topics ?? topics);
      setRequestState({ status: "idle", message: "프로필 벡터를 업데이트했습니다.", error: null });
    } catch {
      setRequestState({ status: "idle", message: null, error: "프로필 벡터 업데이트에 실패했습니다." });
    }
  }

  return (
    <div className="max-w-2xl">
      <h1 className="mb-6 text-2xl font-medium text-dark-text">내 프로필</h1>

      {requestState.message ? (
        <div className="mb-6 flex items-center gap-2 rounded-lg border border-[#137333]/20 bg-[#e6f4ea] p-4 text-sm font-medium text-[#137333]">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{requestState.message}</span>
        </div>
      ) : null}
      {requestState.error ? (
        <div className="mb-6 flex items-center gap-2 rounded-lg border border-[#c5221f]/20 bg-[#fce8e6] p-4 text-sm font-medium text-[#c5221f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{requestState.error}</span>
        </div>
      ) : null}

      <section className="surface mb-6 p-6">
        <div className="mb-6 flex items-center gap-3.5">
          {profile.avatarUrl ? (
            <img
              alt={`${profile.login} GitHub avatar`}
              className="h-14 w-14 rounded-xl border border-dark-border bg-dark-elevated object-cover"
              referrerPolicy="no-referrer"
              src={profile.avatarUrl}
            />
          ) : (
            <div className="flex h-14 w-14 items-center justify-center rounded-xl border border-dark-border bg-dark-elevated text-lg font-semibold text-dark-text">
              {getProfileInitial(profile)}
            </div>
          )}
          <div>
            <p className="text-base font-semibold text-dark-text">{profile.login ? `@${profile.login}` : profile.githubId}</p>
            <p className="font-mono text-xs text-dark-muted">
              {profile.login ? `GitHub ID ${profile.githubId}` : "GitHub ID"}
            </p>
          </div>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          <div>
            <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-dark-muted">이름</label>
            <input
              className="focus-ring w-full rounded-md border border-dark-border bg-white px-3.5 py-2 text-sm text-dark-text placeholder:text-ink-muted focus:border-brand-500"
              onChange={(event) => setName(event.target.value)}
              placeholder="이름을 입력하세요"
              type="text"
              value={name}
            />
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-dark-muted">이메일</label>
            <input
              className="focus-ring w-full rounded-md border border-dark-border bg-white px-3.5 py-2 text-sm text-dark-text placeholder:text-ink-muted focus:border-brand-500"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="이메일을 입력하세요"
              type="email"
              value={email}
            />
          </div>

          <div>
            <label className="mb-1.5 block text-xs font-medium uppercase tracking-wide text-dark-muted">
              관심 기술 토픽
            </label>
            <div className="flex gap-2">
              <input
                className="focus-ring flex-1 rounded-md border border-dark-border bg-white px-3.5 py-2 text-sm text-dark-text placeholder:text-ink-muted focus:border-brand-500"
                onChange={(event) => setTopicInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    handleAddTopic();
                  }
                }}
                placeholder="예: spring-boot, nextjs, pgvector"
                type="text"
                value={topicInput}
              />
              <button
                className="btn-secondary focus-ring inline-flex shrink-0 items-center gap-1 px-3 py-2 text-xs"
                onClick={handleAddTopic}
                type="button"
              >
                <Plus className="h-3.5 w-3.5" />
                <span>토픽 추가</span>
              </button>
            </div>
            {topics.length > 0 ? (
              <div className="mt-2.5 flex flex-wrap gap-1.5">
                {topics.map((topic) => (
                  <span
                    className="badge-trim inline-flex items-center gap-1 rounded-md border border-dark-border bg-dark-elevated px-2.5 py-1 text-xs font-medium text-dark-text"
                    key={topic}
                  >
                    <span>{topic}</span>
                    <button
                      aria-label={`${topic} 토픽 제거`}
                      className="text-ink-muted hover:text-dark-text"
                      onClick={() => handleRemoveTopic(topic)}
                      type="button"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </span>
                ))}
              </div>
            ) : (
              <p className="mt-2 text-xs text-ink-muted">등록된 관심 기술 토픽이 없습니다.</p>
            )}
          </div>

          <button
            className="btn-primary focus-ring inline-flex items-center gap-2 px-5 py-2.5 text-sm disabled:opacity-50"
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

      <section className="surface p-6">
        <div className="mb-1 flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-brand-500" />
          <h2 className="font-medium text-dark-text">프로필 벡터</h2>
        </div>
        <p className="mb-4 text-sm leading-relaxed text-dark-muted">
          GitHub 활동을 분석하여 나에게 맞는 이슈를 추천받기 위한 임베딩 벡터를 생성합니다.
        </p>

        {vectorUpdatedAt ? (
          <p className="tabular-nums mb-4 font-mono text-xs text-dark-muted">
            마지막 업데이트: <span className="font-medium text-dark-text">{formatDate(vectorUpdatedAt)}</span>
          </p>
        ) : null}

        <div className="mb-5 rounded-lg border border-dark-border bg-dark-elevated p-4">
          <p className="mb-2.5 text-xs font-medium uppercase tracking-wide text-dark-muted">감지된 주요 언어</p>
          {profile.primaryLanguages.length > 0 ? (
            <div className="flex flex-wrap gap-2">
              {profile.primaryLanguages.map((language) => (
                <span
                  className="badge-trim rounded-md border border-dark-border bg-white px-2.5 py-0.5 text-xs font-medium text-dark-text"
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
          className="btn-secondary focus-ring flex items-center gap-2 px-4 py-2 text-sm disabled:opacity-50"
          disabled={requestState.status !== "idle"}
          onClick={handleRefreshVector}
          type="button"
        >
          {requestState.status === "refreshing" ? (
            <>
              <Loader2 className="h-4 w-4 animate-spin" />
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
