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
assertIncludes("안녕하세요", "Dashboard page must show the user greeting.");
assertIncludes("aquila buddy", "Dashboard page must use the aquila buddy service name.");
assertIncludes("프로필 분석이 필요합니다", "Dashboard page must show the profile analysis banner.");
assertIncludes("이슈 추천을 받으려면 먼저 마이페이지에서 벡터 업데이트를 완료해주세요.", "Dashboard page must explain the vector update prerequisite.");
assertIncludes("프로필로 이동", "Dashboard page must use the profile navigation CTA.");
assertIncludes("내 계정 정보", "Dashboard page must show the account card.");
assertIncludes("GitHub ID", "Dashboard page must show GitHub ID.");
assertIncludes("프로필 편집", "Dashboard page must link to profile editing.");
assertIncludes("주요 언어", "Dashboard page must show primary languages.");
assertIncludes("벡터 업데이트", "Dashboard page must show vector update status.");
assertIncludes("STEP 1", "Dashboard page must show the first service step.");
assertIncludes("STEP 2", "Dashboard page must show the second service step.");
assertIncludes("STEP 3", "Dashboard page must show the third service step.");
assertIncludes("프로필 분석", "Dashboard page must link profile analysis step.");
assertIncludes("GitHub 활동을 분석하여 AI 추천을 위한 벡터를 생성합니다.", "Dashboard page must describe profile vector creation.");
assertIncludes("이슈 추천", "Dashboard page must link issue recommendation step.");
assertIncludes("나의 기술 스택에 맞는 오픈소스 이슈를 추천받고 분석 힌트를 확인합니다.", "Dashboard page must describe issue recommendation and hints.");
assertIncludes("PR 초안 생성", "Dashboard page must link PR draft step.");
assertIncludes("코드 작업 후 브랜치 정보를 입력하면 AI가 PR을 자동 작성합니다.", "Dashboard page must describe PR draft creation.");

for (const text of [
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
  assertNotIncludes(text, `Dashboard page must not expose old "${text}" structure.`);
}

if (failures.length > 0) {
  console.error("Dashboard page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Dashboard page parity validation passed.");
