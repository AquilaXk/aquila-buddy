import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const issuesPagePath = join(repoRoot, "front/src/app/issues/page.tsx");
const crawlPanelPath = join(repoRoot, "front/src/app/issues/IssueCrawlSearchPanel.tsx");
const issuesPage = readFileSync(issuesPagePath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!issuesPage.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (issuesPage.includes(needle)) {
    fail(message);
  }
}

assertIncludes('"use client";', "Issues page must be a client workflow screen.");
assertIncludes("추천 이슈", "Issues page must show the target title.");
assertIncludes("나의 기술 스택과 GitHub 활동 기반으로 맞춤 오픈소스 이슈를 추천합니다.", "Issues page description changed.");
assertIncludes("이슈 추천받기", "Issues page must expose the recommendation CTA.");
assertIncludes("getUserProfile", "Recommendation flow must read the current user profile.");
assertIncludes("crawlSearchIssues", "Recommendation flow must collect candidate issues by language.");
assertIncludes("getRecommendedIssues", "Recommendation flow must call the recommendation API.");
assertIncludes("getRecommendedIssueHistory", "Issues page must load saved recommendation history.");
assertIncludes("languages.slice(0, 3)", "Recommendation crawl must keep the bounded language collection policy.");
assertIncludes("Promise.all", "Recommendation crawl must keep parallel language collection.");
assertIncludes("사용자 정보 확인 중...", "Recommendation flow must show the user profile step.");
assertIncludes("GitHub에서 이슈 수집 중...", "Recommendation flow must show the crawl step.");
assertIncludes("나에게 맞는 이슈 분석 중...", "Recommendation flow must show the analysis step.");
assertIncludes("프로필 분석을 마쳤다면 바로 추천을 실행해 첫 후보를 받아보세요.", "Empty state must guide profile analysis first.");
assertIncludes("IssueHistoryCard", "Issues page must render history rows.");
assertIncludes("@/components/StatusBadge", "Issues page must import shared StatusBadge component.");
assertIncludes("StatusBadge", "Issues page must render status badges.");
assertIncludes("primer-subnav", "Issues page must attach the filter bar as a unified Primer subnav.");
assertIncludes("divide-y divide-[#d1d9e0]", "Issues page must render issues in a flush divide-y container.");
assertIncludes("border-l-[#0969da] bg-[#f0f6fc]", "Issues page must highlight active keyboard cursor row.");
assertIncludes("<Kbd", "Issues page search input must display keyboard shortcut Kbd hint.");
assertIncludes("tabular-nums", "Issues rows must use tabular-nums on match score.");
assertIncludes("매칭 {scorePercent}%", "Issues rows must show match score.");
assertIncludes("분석 완료", "Issues rows must show analyzed status.");
assertIncludes("추천 근거:", "Issues rows must show a single clean recommendation reason row.");
assertIncludes("상세 분석", "Issues rows must show the detail analysis affordance.");
assertIncludes("북마크한 이슈", "Issues page must expose bookmark filter chip.");
assertIncludes("parseIssueReasonMetadata", "Issues page must safely parse repo metadata from reason.");
assertIncludes("bookmarkIssue", "Issues page must wire bookmark API toggle.");

for (const text of [
  "Sparkles",
  "LowDensityIssueGuide",
  "더 넓게 비교하기",
  "parseRecommendationFacets",
  "🎯",
  "🌱",
  "🔓",
  "⚡",
  "💡",
  "IssueCrawlSearchPanel",
  "Collected catalog",
  "Current recommendations",
  "Saved history",
  "Issue Search",
  "Good First Issue 후보 수집",
  "프로필 분석 완료 후",
  "sheet-header",
]) {
  assertNotIncludes(text, `Issues page must not expose old or slop "${text}" structure.`);
}

if (existsSync(crawlPanelPath)) {
  fail("Standalone crawl search panel should stay removed from the issues page surface.");
}

if (failures.length > 0) {
  console.error("Issues page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Issues page parity validation passed.");
