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
  const [languagesInput, setLanguagesInput] = useState(initialProfile.primaryLanguages.join(", "));
  const [topicsInput, setTopicsInput] = useState(initialProfile.topics.join(", "));
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
        primaryLanguages: splitList(languagesInput),
        topics: splitList(topicsInput)
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
    <section className="space-y-4">
      <div className="surface p-5">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="text-sm font-semibold text-moss">@{profile.login}</p>
            <h2 className="mt-2 text-xl font-bold">{profile.name}</h2>
            <p className="mt-2 text-sm text-neutral-700">{profile.email}</p>
          </div>
          <span className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-neutral-700">
            GitHub ID {profile.githubId}
          </span>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="surface p-4">
          <h3 className="text-sm font-semibold">Profile Status</h3>
          <p className="mt-2 text-lg font-bold text-ink">{vectorLabel}</p>
          <p className="mt-2 text-sm leading-6 text-neutral-700">
            마지막 업데이트 {formatDateTime(profile.vectorUpdatedAt)}
          </p>
        </div>

        <TokenList title="Languages" items={profile.primaryLanguages} />
        <TokenList title="Topics" items={profile.topics} />
      </div>

      <form className="surface space-y-4 p-5" onSubmit={handleSubmit}>
        <div className="grid gap-4 md:grid-cols-2">
          <label className="block text-sm font-semibold text-ink">
            Name
            <input
              className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2 text-sm font-medium"
              value={name}
              onChange={(event) => setName(event.target.value)}
              required
            />
          </label>

          <label className="block text-sm font-semibold text-ink">
            Email
            <input
              className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2 text-sm font-medium"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </label>
        </div>

        <label className="block text-sm font-semibold text-ink">
          Primary Languages
          <input
            className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2 text-sm font-medium"
            value={languagesInput}
            onChange={(event) => setLanguagesInput(event.target.value)}
            required
          />
        </label>

        <label className="block text-sm font-semibold text-ink">
          Topics
          <input
            className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2 text-sm font-medium"
            value={topicsInput}
            onChange={(event) => setTopicsInput(event.target.value)}
            required
          />
        </label>

        {requestState.message ? (
          <p className="rounded-md border border-moss/30 bg-moss/10 px-3 py-2 text-sm font-semibold text-moss">
            {requestState.message}
          </p>
        ) : null}
        {requestState.error ? (
          <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm font-semibold text-red-700">
            {requestState.error}
          </p>
        ) : null}

        <div className="flex flex-wrap gap-3">
          <button
            className="focus-ring rounded-md bg-ink px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:bg-neutral-400"
            type="submit"
            disabled={requestState.status !== "idle"}
          >
            {requestState.status === "saving" ? "Saving..." : "Save profile"}
          </button>
          <button
            className="focus-ring rounded-md border border-line px-4 py-2 text-sm font-semibold text-ink disabled:cursor-not-allowed disabled:text-neutral-400"
            type="button"
            onClick={handleRefreshVector}
            disabled={requestState.status !== "idle"}
          >
            {requestState.status === "refreshing" ? "Refreshing..." : "Refresh vector"}
          </button>
        </div>
      </form>
    </section>
  );
}

function TokenList({ title, items }: { title: string; items: string[] }) {
  return (
    <div className="surface p-4">
      <h3 className="text-sm font-semibold">{title}</h3>
      <div className="mt-3 flex flex-wrap gap-2">
        {items.map((item) => (
          <span key={item} className="rounded-md bg-neutral-100 px-2 py-1 text-xs font-semibold text-neutral-700">
            {item}
          </span>
        ))}
      </div>
    </div>
  );
}

function splitList(value: string) {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Seoul"
  }).format(new Date(value));
}
