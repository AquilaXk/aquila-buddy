import { ProtectedLayout } from "@/components/ProtectedLayout";
import { getUserProfile, getUserProfileByLogin } from "@/lib/api";
import { ProfileEditorPanel } from "./ProfileEditorPanel";

export const dynamic = "force-dynamic";

export default async function ProfilePage() {
  const result = await loadUserProfile();

  return (
    <ProtectedLayout>
      {result.error ? (
        <div className="surface mb-4 border-red-500/30 bg-red-500/10 p-4 text-sm font-semibold text-red-400">
          프로필을 불러오지 못했습니다. backend API 상태를 확인해 주세요.
        </div>
      ) : null}
      {result.profile ? (
        <ProfileEditorPanel initialProfile={result.profile} publicProfile={result.publicProfile} />
      ) : (
        <section className="surface p-5">
          <h2 className="text-lg font-bold">Profile</h2>
          <p className="mt-2 text-sm leading-6 text-github-muted">프로필 데이터가 준비되지 않았습니다.</p>
        </section>
      )}
    </ProtectedLayout>
  );
}

async function loadUserProfile() {
  try {
    const profile = await getUserProfile();
    let publicProfile = null;

    try {
      publicProfile = await getUserProfileByLogin(profile.login);
    } catch {
      publicProfile = null;
    }

    return {
      profile,
      publicProfile,
      error: null
    };
  } catch (error) {
    return {
      profile: null,
      publicProfile: null,
      error
    };
  }
}
