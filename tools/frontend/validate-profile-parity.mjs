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

assertIncludes(profilePage, "getUserProfile", "Profile page must load the current user profile.");
assertIncludes(profilePage, "<ProfileEditorPanel initialProfile={result.profile} />", "Profile page must render the simplified editor panel.");
assertNotIncludes(profilePage, "getUserProfileByLogin", "Profile page must not fetch the public profile panel.");
assertNotIncludes(profilePage, "publicProfile", "Profile page must not pass public profile data.");

for (const text of [
  "내 프로필",
  "GitHub ID",
  "이름",
  "이메일",
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
assertIncludes(profilePanel, "topics: profile.topics", "Profile save must preserve detected topics.");
assertIncludes(profilePanel, "refreshUserProfileVector", "Profile panel must keep vector refresh behavior.");
assertIncludes(profilePanel, "requestState.status === \"saving\"", "Profile panel must expose the saving fixture state.");
assertIncludes(profilePanel, "requestState.status === \"refreshing\"", "Profile panel must expose the vector refresh fixture state.");
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
