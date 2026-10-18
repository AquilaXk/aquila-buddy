"use client";

import { useState } from "react";
import { Save, RefreshCw, Loader2, CheckCircle2, AlertCircle, Plus, X } from "lucide-react";
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
    <div>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-neutral-border pb-5">
        <div className="flex items-center gap-3">
          {profile.avatarUrl ? (
            <img
              alt={`${profile.login} GitHub avatar`}
              className="h-10 w-10 rounded-full border border-neutral-border bg-neutral-subtle object-cover"
              referrerPolicy="no-referrer"
              src={profile.avatarUrl}
            />
          ) : (
            <div className="flex h-10 w-10 items-center justify-center rounded-full border border-neutral-border bg-neutral-subtle text-sm font-semibold text-ink-primary">
              {getProfileInitial(profile)}
            </div>
          )}
          <div>
            <h1 className="text-xl font-semibold tracking-[-0.02em] text-ink-primary">내 프로필</h1>
            <p className="font-mono text-xs text-ink-secondary">
              {profile.login ? `@${profile.login} · GitHub ID ${profile.githubId}` : `GitHub ID ${profile.githubId}`}
            </p>
          </div>
        </div>
      </header>

      {requestState.message ? (
        <div className="mb-5 flex items-center gap-2 rounded border border-[#137333]/20 bg-[#e6f4ea] px-4 py-3 text-xs font-medium text-[#137333]">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{requestState.message}</span>
        </div>
      ) : null}
      {requestState.error ? (
        <div className="mb-5 flex items-center gap-2 rounded border border-[#c5221f]/20 bg-[#fce8e6] px-4 py-3 text-xs font-medium text-[#c5221f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{requestState.error}</span>
        </div>
      ) : null}

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <section className="surface overflow-hidden lg:col-span-7">
          <div className="sheet-header px-5 py-3">
            <h2 className="text-xs font-semibold text-ink-primary">계정 및 관심 기술 토픽</h2>
          </div>

          <form className="divide-y divide-neutral-border" onSubmit={handleSubmit}>
            <div className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-xs font-medium text-ink-secondary">이름</label>
                <input
                  className="focus-ring w-full rounded border border-neutral-border bg-white px-3 py-1.5 text-xs text-ink-primary placeholder:text-ink-muted focus:border-brand-500"
                  onChange={(event) => setName(event.target.value)}
                  placeholder="이름을 입력하세요"
                  type="text"
                  value={name}
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-ink-secondary">이메일</label>
                <input
                  className="focus-ring w-full rounded border border-neutral-border bg-white px-3 py-1.5 text-xs text-ink-primary placeholder:text-ink-muted focus:border-brand-500"
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="이메일을 입력하세요"
                  type="email"
                  value={email}
                />
              </div>
            </div>

            <div className="p-5">
              <label className="mb-1.5 block text-xs font-medium text-ink-secondary">
                관심 기술 토픽
              </label>
              <div className="flex gap-2">
                <input
                  className="focus-ring flex-1 rounded border border-neutral-border bg-white px-3 py-1.5 font-mono text-xs text-ink-primary placeholder:text-ink-muted focus:border-brand-500"
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
                  className="btn-secondary focus-ring inline-flex shrink-0 items-center gap-1 px-3 py-1.5 text-xs"
                  onClick={handleAddTopic}
                  type="button"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span className="button-text">토픽 추가</span>
                </button>
              </div>
              {topics.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {topics.map((topic) => (
                    <span
                      className="badge-trim inline-flex items-center gap-1 rounded border border-neutral-border bg-neutral-subtle px-2 py-0.5 font-mono text-xs text-ink-primary"
                      key={topic}
                    >
                      <span>{topic}</span>
                      <button
                        aria-label={`${topic} 토픽 제거`}
                        className="text-ink-muted hover:text-ink-primary"
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

            <div className="flex justify-end bg-neutral-subtle px-5 py-3">
              <button
                className="btn-primary focus-ring inline-flex items-center gap-1.5 px-4 py-1.5 text-xs disabled:opacity-50"
                disabled={requestState.status !== "idle"}
                type="submit"
              >
                {requestState.status === "saving" ? (
                  <>
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    <span className="button-text">저장 중...</span>
                  </>
                ) : (
                  <>
                    <Save className="h-3.5 w-3.5" />
                    <span className="button-text">프로필 저장</span>
                  </>
                )}
              </button>
            </div>
          </form>
        </section>

        <section className="surface divide-y divide-neutral-border overflow-hidden lg:col-span-5">
          <div className="sheet-header flex items-center justify-between gap-2 px-5 py-3">
            <h2 className="text-xs font-semibold text-ink-primary">프로필 벡터</h2>
            {vectorUpdatedAt ? (
              <span className="tabular-nums font-mono text-[11px] text-ink-muted">
                {formatDate(vectorUpdatedAt)}
              </span>
            ) : (
              <span className="text-[11px] font-medium text-[#b06000]">동기화 필요</span>
            )}
          </div>

          <div className="p-5">
            <p className="text-xs leading-relaxed text-ink-secondary">
              GitHub 활동을 분석하여 나에게 맞는 이슈를 추천받기 위한 임베딩 벡터를 생성합니다.
            </p>

            <div className="mt-4">
              <p className="mb-2 text-xs font-semibold text-ink-primary">감지된 주요 언어</p>
              {profile.primaryLanguages.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {profile.primaryLanguages.map((language) => (
                    <span
                      className="badge-trim rounded border border-neutral-border bg-neutral-subtle px-2 py-0.5 font-mono text-xs font-medium text-ink-primary"
                      key={language}
                    >
                      {language}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-ink-muted">아직 분석된 주요 언어가 없습니다.</p>
              )}
            </div>
          </div>

          <div className="bg-neutral-subtle px-5 py-3">
            <button
              className="btn-secondary focus-ring inline-flex w-full items-center justify-center gap-1.5 px-3.5 py-1.5 text-xs disabled:opacity-50"
              disabled={requestState.status !== "idle"}
              onClick={handleRefreshVector}
              type="button"
            >
              {requestState.status === "refreshing" ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span className="button-text">분석 중... (시간이 걸릴 수 있습니다)</span>
                </>
              ) : (
                <>
                  <RefreshCw className="h-3.5 w-3.5" />
                  <span className="button-text">벡터 업데이트</span>
                </>
              )}
            </button>
          </div>
        </section>
      </div>
    </div>
  );
}

function getProfileInitial(profile: UserProfile) {
  return (profile.name || profile.login || String(profile.githubId) || "?").at(0)?.toUpperCase() ?? "?";
}
