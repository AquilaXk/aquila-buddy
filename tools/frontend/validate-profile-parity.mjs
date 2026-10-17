import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const profilePagePath = join(repoRoot, "front/src/app/profile/page.tsx");
const profilePanelPath = join(repoRoot, "front/src/app/profile/ProfileEditorPanel.tsx");
const profilePage = readFileSync(profilePagePath, "utf8");
const profilePanel = readFileSync(profilePanelPath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(text, needle, message) {
  if (!text.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(text, needle, message) {
  if (text.includes(needle)) {
    fail(message);
  }
}

assertIncludes(profilePage, '"use client";', "Profile page must be a Client Component so browser auth cookies are sent to the backend.");
assertIncludes(profilePage, "useEffect", "Profile page must load the current user profile from the browser.");
assertIncludes(profilePage, "useState", "Profile page must manage loading/profile/error state client-side.");
assertIncludes(profilePage, "getUserProfile", "Profile page must load the current user profile.");
assertIncludes(profilePage, "<ProfileEditorPanel initialProfile={profile} />", "Profile page must render the simplified editor panel.");
assertIncludes(profilePage, "프로필을 불러오는 중...", "Profile page must show a loading state while fetching from the browser.");
assertIncludes(profilePage, "프로필을 불러오지 못했습니다", "Profile page must show an error banner when loading fails.");
assertNotIncludes(profilePage, "backend API 상태를 확인해 주세요.", "Profile page must not print generic backend API status slop.");
assertNotIncludes(profilePage, "async function ProfilePage", "Profile page must not fetch the authenticated profile during server render.");
assertNotIncludes(profilePage, "result.profile", "Profile page must not depend on server-loaded profile results.");
assertNotIncludes(profilePage, "getUserProfileByLogin", "Profile page must not fetch the public profile panel.");
assertNotIncludes(profilePage, "publicProfile", "Profile page must not pass public profile data.");

for (const text of [
  "내 프로필",
  "GitHub ID",
  "이름",
  "이메일",
  "관심 기술 토픽",
  "프로필 저장",
  "프로필을 저장했습니다.",
  "프로필 저장에 실패했습니다.",
  "프로필 벡터",
  "GitHub 활동을 분석하여 나에게 맞는 이슈를 추천받기 위한 임베딩 벡터를 생성합니다.",
  "감지된 주요 언어",
  "아직 분석된 주요 언어가 없습니다.",
  "벡터 업데이트",
  "분석 중... (시간이 걸릴 수 있습니다)",
  "프로필 벡터를 업데이트했습니다.",
  "프로필 벡터 업데이트에 실패했습니다."
]) {
  assertIncludes(profilePanel, text, `Profile panel is missing "${text}".`);
}

assertIncludes(profilePanel, "primaryLanguages: profile.primaryLanguages", "Profile save must preserve detected languages.");
assertIncludes(profilePanel, "topics", "Profile save and editor must manage topics.");
assertIncludes(profilePanel, "refreshUserProfileVector", "Profile panel must keep vector refresh behavior.");
assertIncludes(profilePanel, "requestState.status === \"saving\"", "Profile panel must expose the saving fixture state.");
assertIncludes(profilePanel, "requestState.status === \"refreshing\"", "Profile panel must expose the vector refresh fixture state.");
assertIncludes(profilePanel, "@/lib/format", "Profile panel must use shared formatDate utility.");
assertNotIncludes(profilePanel, "border-t-transparent", "Profile panel must unify loading spinners on Loader2 instead of ad-hoc border spinners.");
assertNotIncludes(profilePanel, "required", "Profile form must not block save/error fixtures with browser-only required constraints.");

for (const text of [
  "Public Profile",
  "Profile Status",
  "Primary Languages",
  "Save profile",
  "Refresh vector",
  "TokenList"
]) {
  assertNotIncludes(profilePanel, text, `Profile panel must not use old "${text}" structure.`);
}

if (failures.length > 0) {
  console.error("Profile page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Profile page parity validation passed.");
