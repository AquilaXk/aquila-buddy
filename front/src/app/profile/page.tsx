import { ProtectedLayout } from "@/components/ProtectedLayout";
import { getUserProfile } from "@/lib/api";

export const dynamic = "force-dynamic";

export default async function ProfilePage() {
  const result = await loadUserProfile();

  return (
    <ProtectedLayout title="Profile">
      {result.error ? (
        <div className="surface mb-4 border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">
          프로필을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      {result.profile ? (
        <section className="space-y-4">
          <div className="surface p-5">
            <div className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-moss">@{result.profile.login}</p>
                <h2 className="mt-2 text-xl font-bold">{result.profile.name}</h2>
                <p className="mt-2 text-sm text-neutral-700">{result.profile.email}</p>
              </div>
              <span className="rounded-md border border-line px-3 py-2 text-sm font-semibold text-neutral-700">
                GitHub ID {result.profile.githubId}
              </span>
            </div>
          </div>

          <div className="grid gap-4 md:grid-cols-3">
            <div className="surface p-4">
              <h3 className="text-sm font-semibold">Profile Status</h3>
              <p className="mt-2 text-lg font-bold text-ink">
                {result.profile.profileReady ? "Ready" : "Pending"}
              </p>
              <p className="mt-2 text-sm leading-6 text-neutral-700">
                마지막 업데이트 {formatDateTime(result.profile.vectorUpdatedAt)}
              </p>
            </div>

            <div className="surface p-4">
              <h3 className="text-sm font-semibold">Languages</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                {result.profile.primaryLanguages.map((language) => (
                  <span
                    key={language}
                    className="rounded-md bg-neutral-100 px-2 py-1 text-xs font-semibold text-neutral-700"
                  >
                    {language}
                  </span>
                ))}
              </div>
            </div>

            <div className="surface p-4">
              <h3 className="text-sm font-semibold">Topics</h3>
              <div className="mt-3 flex flex-wrap gap-2">
                {result.profile.topics.map((topic) => (
                  <span
                    key={topic}
                    className="rounded-md bg-neutral-100 px-2 py-1 text-xs font-semibold text-neutral-700"
                  >
                    {topic}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </section>
      ) : (
        <section className="surface p-5">
          <h2 className="text-lg font-bold">Profile</h2>
          <p className="mt-2 text-sm leading-6 text-neutral-700">프로필 데이터가 준비되지 않았습니다.</p>
        </section>
      )}
    </ProtectedLayout>
  );
}

async function loadUserProfile() {
  try {
    return {
      profile: await getUserProfile(),
      error: null
    };
  } catch (error) {
    return {
      profile: null,
      error
    };
  }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "Asia/Seoul"
  }).format(new Date(value));
}
