"use client";

import { useId, useState } from "react";
import { AlertCircle, CheckCircle2, Loader2, Plus, RefreshCw, Save, X } from "@/components/icons";
import { Badge } from "@/components/ui/badge";
import {
  refreshUserProfileVector,
  updateUserProfile,
  type UpdateUserProfileRequest,
  type UserProfile
} from "@/lib/api";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

const PRESET_LANGUAGES = [
  "Java",
  "TypeScript",
  "JavaScript",
  "Python",
  "Go",
  "Rust",
  "Kotlin",
  "C++",
];

type ProfileEditorPanelProps = {
  initialProfile: UserProfile;
};

type RequestState = {
  status: "idle" | "saving" | "refreshing";
  message: string | null;
  error: string | null;
};

export function ProfileEditorPanel({ initialProfile }: ProfileEditorPanelProps) {
  const nameInputId = useId();
  const emailInputId = useId();
  const languageInputId = useId();
  const topicInputId = useId();

  const [profile, setProfile] = useState(initialProfile);
  const [name, setName] = useState(initialProfile.name);
  const [email, setEmail] = useState(initialProfile.email);
  const [primaryLanguages, setPrimaryLanguages] = useState<string[]>(initialProfile.primaryLanguages ?? []);
  const [languageInput, setLanguageInput] = useState("");
  const [topics, setTopics] = useState<string[]>(initialProfile.topics ?? []);
  const [topicInput, setTopicInput] = useState("");
  const vectorUpdatedAt = profile.profileReady && profile.vectorUpdatedAt ? profile.vectorUpdatedAt : null;
  const [requestState, setRequestState] = useState<RequestState>({
    status: "idle",
    message: null,
    error: null
  });

  function hasLanguageIgnoreCase(list: string[], target: string): boolean {
    const lower = target.toLowerCase();
    return list.some((item) => item.toLowerCase() === lower);
  }

  function handleTogglePresetLanguage(language: string) {
    setPrimaryLanguages((prev) => {
      const lower = language.toLowerCase();
      if (prev.some((item) => item.toLowerCase() === lower)) {
        return prev.filter((item) => item.toLowerCase() !== lower);
      }
      return [...prev, language];
    });
  }

  function handleAddLanguage() {
    const trimmed = languageInput.trim();
    if (!trimmed) {
      return;
    }
    if (!hasLanguageIgnoreCase(primaryLanguages, trimmed)) {
      setPrimaryLanguages((prev) => [...prev, trimmed]);
    }
    setLanguageInput("");
  }

  function handleRemoveLanguage(target: string) {
    const lower = target.toLowerCase();
    setPrimaryLanguages((prev) => prev.filter((item) => item.toLowerCase() !== lower));
  }

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
        primaryLanguages,
        topics
      };
      const updated = await updateUserProfile(request);
      setProfile(updated);
      setPrimaryLanguages(updated.primaryLanguages ?? primaryLanguages);
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
      setPrimaryLanguages(refreshed.primaryLanguages ?? primaryLanguages);
      setTopics(refreshed.topics ?? topics);
      setRequestState({ status: "idle", message: "추천 기준을 동기화했습니다.", error: null });
    } catch {
      setRequestState({ status: "idle", message: null, error: "추천 기준 동기화에 실패했습니다." });
    }
  }

  return (
    <div>
      <header className="mb-6 flex flex-wrap items-center justify-between gap-4 border-b border-[#d1d9e0] pb-5">
        <div className="flex items-center gap-3.5">
          {profile.avatarUrl ? (
            <img
              alt={`${profile.login} GitHub avatar`}
              className="h-11 w-11 rounded-full border border-[#d1d9e0] bg-[#f6f8fa] object-cover"
              referrerPolicy="no-referrer"
              src={profile.avatarUrl}
            />
          ) : (
            <div className="flex h-11 w-11 items-center justify-center rounded-full border border-[#0969da]/25 bg-[#ddf4ff] text-sm font-semibold text-[#0969da]">
              {getProfileInitial(profile)}
            </div>
          )}
          <div>
            <h1 className="text-[22px] font-semibold leading-[1.3] tracking-[-0.025em] text-[#1f2328]">
              내 프로필
            </h1>
            <p className="mt-0.5 font-mono text-[12px] font-medium tabular-nums text-[#59636e]">
              {profile.login ? `@${profile.login} · GitHub ID ${profile.githubId}` : `GitHub ID ${profile.githubId}`}
            </p>
          </div>
        </div>
      </header>

      {requestState.message ? (
        <div className="mb-5 flex items-center gap-2 rounded-lg border border-[#1a7f37]/30 bg-[#dafbe1] px-4 py-3 text-[13px] font-medium text-[#1a7f37]">
          <CheckCircle2 className="h-4 w-4 shrink-0" />
          <span>{requestState.message}</span>
        </div>
      ) : null}
      {requestState.error ? (
        <div className="mb-5 flex items-center gap-2 rounded-lg border border-[#ff8182]/40 bg-[#ffebe9] px-4 py-3 text-[13px] font-medium text-[#d1242f]">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{requestState.error}</span>
        </div>
      ) : null}

      <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
        <section className="primer-box overflow-hidden lg:col-span-7">
          <div className="primer-box-header px-5 py-3">
            <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
              계정 및 기술 스택 설정
            </h2>
          </div>

          <form className="divide-y divide-[#d1d9e0]" onSubmit={handleSubmit}>
            <div className="grid grid-cols-1 gap-4 p-5 sm:grid-cols-2">
              <div>
                <label className="mb-1.5 block text-[13px] font-semibold text-[#1f2328]" htmlFor={nameInputId}>
                  이름
                </label>
                <input
                  autoComplete="name"
                  className="focus-ring w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 text-[13.5px] text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
                  id={nameInputId}
                  onChange={(event) => setName(event.target.value)}
                  placeholder="이름을 입력하세요"
                  type="text"
                  value={name}
                />
              </div>

              <div>
                <label className="mb-1.5 block text-[13px] font-semibold text-[#1f2328]" htmlFor={emailInputId}>
                  이메일
                </label>
                <input
                  autoComplete="email"
                  className="focus-ring w-full rounded-md border border-[#d1d9e0] bg-white px-3 py-2 text-[13.5px] text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
                  id={emailInputId}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="이메일을 입력하세요"
                  type="email"
                  value={email}
                />
              </div>
            </div>

            <div className="p-5">
              <label className="mb-2 block text-[13px] font-semibold text-[#1f2328]" htmlFor={languageInputId}>
                주요 프로그래밍 언어
              </label>
              <div className="mb-3 flex flex-wrap gap-1.5" role="group" aria-label="대표 언어 빠른 선택">
                {PRESET_LANGUAGES.map((language) => {
                  const selected = hasLanguageIgnoreCase(primaryLanguages, language);
                  return (
                    <button
                      aria-pressed={selected}
                      className={cn(
                        "focus-ring rounded-md border px-2.5 py-1 font-mono text-xs transition-colors",
                        selected
                          ? "border-[#0969da]/30 bg-[#ddf4ff] font-semibold text-[#0969da]"
                          : "border-[#d1d9e0] bg-[#f6f8fa] font-medium text-[#59636e] hover:border-[#b9c3ce] hover:text-[#1f2328]"
                      )}
                      key={language}
                      onClick={() => handleTogglePresetLanguage(language)}
                      type="button"
                    >
                      {language}
                    </button>
                  );
                })}
              </div>

              <div className="flex gap-2">
                <input
                  className="focus-ring flex-1 rounded-md border border-[#d1d9e0] bg-white px-3 py-2 font-mono text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
                  id={languageInputId}
                  onChange={(event) => setLanguageInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.nativeEvent.isComposing || event.keyCode === 229) {
                      return;
                    }
                    if (event.key === "Enter") {
                      event.preventDefault();
                      handleAddLanguage();
                    } else if (event.key === "Backspace" && !languageInput && primaryLanguages.length > 0) {
                      handleRemoveLanguage(primaryLanguages[primaryLanguages.length - 1]);
                    }
                  }}
                  placeholder="직접 입력 (예: Scala, Swift, Ruby)"
                  type="text"
                  value={languageInput}
                />
                <button
                  className="btn-secondary focus-ring inline-flex shrink-0 items-center gap-1 px-3 py-2 text-xs"
                  onClick={handleAddLanguage}
                  type="button"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span className="button-text">언어 추가</span>
                </button>
              </div>

              {primaryLanguages.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {primaryLanguages.map((language) => (
                    <span
                      className="badge-trim inline-flex items-center gap-1.5 rounded-full border border-[#1a7f37]/30 bg-[#dafbe1] px-2.5 py-0.5 font-mono text-xs font-medium text-[#1a7f37]"
                      key={language}
                    >
                      <span>{language}</span>
                      <button
                        aria-label={`${language} 언어 제거`}
                        className="text-[#1a7f37]/75 hover:text-[#1f2328]"
                        onClick={() => handleRemoveLanguage(language)}
                        type="button"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              ) : (
                <p className="mt-2 text-xs text-[#59636e]">선택된 주요 프로그래밍 언어가 없습니다.</p>
              )}
            </div>

            <div className="p-5">
              <label className="mb-2 block text-[13px] font-semibold text-[#1f2328]" htmlFor={topicInputId}>
                관심 기술 토픽
              </label>
              <div className="flex gap-2">
                <input
                  className="focus-ring flex-1 rounded-md border border-[#d1d9e0] bg-white px-3 py-2 font-mono text-xs text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
                  id={topicInputId}
                  onChange={(event) => setTopicInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.nativeEvent.isComposing || event.keyCode === 229) {
                      return;
                    }
                    if (event.key === "Enter") {
                      event.preventDefault();
                      handleAddTopic();
                    } else if (event.key === "Backspace" && !topicInput && topics.length > 0) {
                      handleRemoveTopic(topics[topics.length - 1]);
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
                  <span className="button-text">토픽 추가</span>
                </button>
              </div>
              {topics.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-1.5">
                  {topics.map((topic) => (
                    <span
                      className="badge-trim inline-flex items-center gap-1.5 rounded-full border border-[#0969da]/25 bg-[#ddf4ff] px-2.5 py-0.5 font-mono text-xs font-medium text-[#0969da]"
                      key={topic}
                    >
                      <span>{topic}</span>
                      <button
                        aria-label={`${topic} 토픽 제거`}
                        className="text-[#0969da]/75 hover:text-[#1f2328]"
                        onClick={() => handleRemoveTopic(topic)}
                        type="button"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              ) : (
                <p className="mt-2 text-xs text-[#59636e]">등록된 관심 기술 토픽이 없습니다.</p>
              )}
            </div>

            <div className="flex justify-end bg-[#f6f8fa] px-5 py-3">
              <button
                className="btn-primary focus-ring inline-flex items-center gap-1.5 px-4 py-2 text-xs disabled:opacity-50"
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

        <section className="primer-box divide-y divide-[#d1d9e0] overflow-hidden lg:col-span-5">
          <div className="primer-box-header flex items-center justify-between gap-2 px-5 py-3">
            <h2 className="text-[14px] font-semibold tracking-[-0.015em] text-[#1f2328]">
              GitHub 활동 분석
            </h2>
            {vectorUpdatedAt ? (
              <span className="tabular-nums font-mono text-[11.5px] font-medium text-[#59636e]">
                {formatDate(vectorUpdatedAt)}
              </span>
            ) : (
              <Badge variant="warning">동기화 필요</Badge>
            )}
          </div>

          <div className="p-5">
            <p className="text-[13.5px] leading-[1.55] text-[#59636e]">
              GitHub 활동을 분석해 내 기술 스택에 맞는 이슈 추천 기준을 동기화합니다.
            </p>

            <div className="mt-4">
              <p className="mb-2 text-[13px] font-semibold text-[#1f2328]">감지된 주요 언어</p>
              {profile.primaryLanguages.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {profile.primaryLanguages.map((language) => (
                    <Badge key={language} variant="accent">
                      <span className="font-mono">{language}</span>
                    </Badge>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-[#59636e]">아직 분석된 주요 언어가 없습니다.</p>
              )}
            </div>
          </div>

          <div className="bg-[#f6f8fa] px-5 py-3">
            <button
              className="btn-secondary focus-ring inline-flex w-full items-center justify-center gap-1.5 px-3.5 py-2 text-xs disabled:opacity-50"
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
                  <span className="button-text">추천 기준 동기화</span>
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
