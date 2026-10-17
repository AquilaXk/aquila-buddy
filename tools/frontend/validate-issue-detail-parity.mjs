import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const issueDetailPagePath = join(repoRoot, "front/src/app/issues/[id]/page.tsx");
const issueDetailDataPath = join(repoRoot, "front/src/app/issues/[id]/issueDetailData.ts");
const issueDetailPage = readFileSync(issueDetailPagePath, "utf8");
const apiSource = readFileSync(join(repoRoot, "front/src/lib/api.ts"), "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!issueDetailPage.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (issueDetailPage.includes(needle)) {
    fail(message);
  }
}

function assertApiIncludes(needle, message) {
  if (!apiSource.includes(needle)) {
    fail(message);
  }
}

if (existsSync(issueDetailDataPath)) {
  fail("front/src/app/issues/[id]/issueDetailData.ts must stay deleted.");
}

assertIncludes("\"use client\";", "Issue detail page must be a client screen.");
assertIncludes("useParams", "Issue detail page must read the route id on the client.");
assertIncludes("getIssueDetail", "Issue detail page must use the issue detail API first.");
assertIncludes("getRecommendedIssueHistory", "Issue detail page must load saved recommendation history.");
assertIncludes("getRecommendedIssues", "Issue detail page must fall back to current recommendations.");
assertIncludes("getIssueGuide", "Issue detail page must request the guide on demand.");
assertIncludes("getIssuePseudoCode", "Issue detail page must request pseudo code on demand.");
assertIncludes("handleGetGuide", "Issue detail page must expose a guide request handler.");
assertIncludes("handleGetPseudo", "Issue detail page must expose a pseudo code request handler.");
assertIncludes("normalizeIssueGuide", "Issue detail page must normalize guide responses before rendering optional arrays.");
assertIncludes("normalizeIssuePseudoCode", "Issue detail page must normalize pseudo-code responses before rendering optional arrays.");
assertIncludes("setGuide(normalizeIssueGuide(await getIssueGuide(issueId)))", "Guide handler must store a normalized guide response.");
assertIncludes("setPseudo(normalizeIssuePseudoCode(await getIssuePseudoCode(issueId)))", "Pseudo handler must store a normalized pseudo-code response.");
assertIncludes("Array.isArray", "Issue detail page must guard optional array fields with Array.isArray.");
assertIncludes("이슈를 찾을 수 없습니다.", "Issue detail page must keep the not-found state.");
assertIncludes("추천 이슈 목록", "Issue detail page must link back to the issues list.");
assertIncludes("추천 이유", "Issue detail page must show the recommendation reason.");
assertIncludes("{issue.reason}", "Issue detail page must render recommendation reason from issue.reason, not the raw issue body summary.");
assertNotIncludes("{issue.summary}</p>", "Issue detail page must not render raw issue body summary as the recommendation reason.");
assertIncludes("매칭", "Issue detail page must show the match score.");
assertIncludes("@/components/StatusBadge", "Issue detail page must import shared StatusBadge.");
assertIncludes("StatusBadge", "Issue detail page must render status badges.");
assertIncludes("1차 힌트 — 수정 가이드", "Issue detail page must show the first hint card.");
assertIncludes("힌트 보기", "Issue detail page must expose the guide CTA.");
assertIncludes("2차 힌트 — 의사 코드", "Issue detail page must show the second hint card.");
assertIncludes("코드 힌트 보기", "Issue detail page must expose the pseudo-code CTA.");
assertIncludes("PR 초안 생성", "Issue detail page must show the PR draft CTA section.");
assertIncludes("PR 초안 만들기 →", "Issue detail page must expose the PR draft link.");
assertIncludes("수정 대상 파일", "Issue detail page must show the file path section label.");
assertIncludes("수정 대상 파일 없음", "Issue detail page must keep the empty file path result structure.");
assertIncludes("guide.sideEffects.length > 0", "Issue detail page must keep the side effects result section.");
assertIncludes("guideLoading ? \"분석 중...\" : \"힌트 보기\"", "Issue detail page must keep the guide loading fixture.");
assertIncludes("pseudoLoading ? \"분석 중...\" : \"코드 힌트 보기\"", "Issue detail page must keep the pseudo loading fixture.");
assertIncludes("buildPullRequestDraftHref(issue)", "buildPullRequestDraftHref must not pass unused guide parameter.");

assertApiIncludes("export async function getIssueDetail", "Frontend API client must expose issue detail lookup.");
assertApiIncludes("/api/v1/issues/${encodeURIComponent(id)}", "Issue detail lookup must call the id detail API.");
assertApiIncludes("Issue detail response was not successful", "Issue detail lookup must validate the response envelope.");

for (const queryContract of [
  "issueNumber: String(issue.issueNumber)",
  "issueId: issue.id",
  "issueTitle: issue.title",
  "sourceIssueId: issue.id"
]) {
  assertIncludes(queryContract, `Issue detail page must keep PR draft query contract: ${queryContract}.`);
}

for (const text of [
  "Radio",
  "Sparkles",
  "IssueGuideWorkflowPanel",
  "IssuePseudoCodePanel",
  "loadIssueDetailOverview",
  "Guide Workflow",
  "Pseudo Code",
  "PR Draft"
]) {
  assertNotIncludes(text, `Issue detail page must not expose unused or old "${text}" structure.`);
}

if (failures.length > 0) {
  console.error("Issue detail page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Issue detail page parity validation passed.");
