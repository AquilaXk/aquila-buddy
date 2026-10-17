import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const dashboardPagePath = join(repoRoot, "front/src/app/dashboard/page.tsx");
const dashboardPage = readFileSync(dashboardPagePath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!dashboardPage.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (dashboardPage.includes(needle)) {
    fail(message);
  }
}

assertIncludes("\"use client\";", "Dashboard page must be a client screen.");
assertIncludes("useEffect", "Dashboard page must load the current user on the client.");
assertIncludes("useState", "Dashboard page must keep loading/error/profile state.");
assertIncludes("getUserProfile", "Dashboard page must fetch the current user profile.");
assertIncludes("formatDate", "Dashboard page must format vectorUpdatedAt using shared formatDate.");
assertIncludes("@/lib/format", "Dashboard page must import formatDate from @/lib/format.");
assertIncludes("안녕하세요", "Dashboard page must show the user greeting.");
assertIncludes("기여 워크스페이스 개요", "Dashboard page must use the clean Korean subtitle.");
assertIncludes("{error}", "Dashboard page must render the actual error message when present.");
assertIncludes("프로필 분석이 필요합니다", "Dashboard page must show the profile analysis banner.");
assertIncludes("const hasVector = Boolean(user?.profileReady || user?.vectorUpdatedAt);", "Dashboard must gate recommendation entry from profile vector readiness.");
assertIncludes("이슈 추천을 받으려면 먼저 마이페이지에서 벡터 업데이트를 완료해주세요.", "Dashboard page must explain the vector update prerequisite.");
assertIncludes("프로필로 이동", "Dashboard page must use the profile navigation CTA.");
assertIncludes("내 계정 정보", "Dashboard page must show the account card.");
assertIncludes("GitHub ID", "Dashboard page must show GitHub ID.");
assertIncludes("프로필 편집", "Dashboard page must link to profile editing.");
assertIncludes("주요 언어", "Dashboard page must show primary languages.");
assertIncludes("벡터 업데이트", "Dashboard page must show vector update status.");
assertIncludes("프로필 분석", "Dashboard page must link profile analysis action.");
assertIncludes("이슈 추천", "Dashboard page must link issue recommendation action.");
assertIncludes("PR 초안 생성", "Dashboard page must link PR draft action.");

for (const text of [
  "OpenSource contribution assistant 대시보드",
  "backend API 상태를 확인해 주세요.",
  "STEP 1",
  "STEP 2",
  "STEP 3",
  "DashboardActivityPanel",
  "DashboardWorkflowPanel",
  "loadDashboardOverview",
  "buildDashboardActivities",
  "buildDashboardWorkflow",
  "Recent Issues",
  "Recent PR Drafts",
  "Translations",
  "Workspace"
]) {
  assertNotIncludes(text, `Dashboard page must not expose old or slop "${text}" structure.`);
}

if (failures.length > 0) {
  console.error("Dashboard page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Dashboard page parity validation passed.");
