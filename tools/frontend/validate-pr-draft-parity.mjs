import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const prDraftPagePath = join(repoRoot, "front/src/app/pr-draft/page.tsx");
const prDraftPage = readFileSync(prDraftPagePath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!prDraftPage.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (prDraftPage.includes(needle)) {
    fail(message);
  }
}

assertIncludes("useSearchParams", "PR draft page must read issue context from query params.");
assertIncludes("createPullRequestDraft", "PR draft page must create a draft through the API.");
assertIncludes("getIssueDetail", "PR draft page must prefill missing source issue fields from the detail API.");
assertIncludes("translatePullRequestDraft", "PR draft page must expose translation through the API.");
assertIncludes("getPullRequestDraftHistory", "PR draft page must load draft history.");
assertIncludes("sourceIssueLoading", "PR draft page must expose source issue lookup loading state.");
assertIncludes("sourceIssueError", "PR draft page must expose source issue lookup fallback error state.");
assertIncludes("setDraftInput", "PR draft page must keep mutable draft input after source issue lookup.");
assertIncludes("initialDraftInput.issueId", "PR draft page must trigger lookup from issueId/sourceIssueId.");
assertIncludes("mergeDraftInputWithIssue", "PR draft page must isolate query-first source issue merging.");
assertIncludes("currentInput.upstreamRepo || issue.repoFullName", "PR draft page must keep query repo before detail API repo.");
assertIncludes("PR 초안 생성", "PR draft page must show the target title.");
assertIncludes("← 이슈로 돌아가기", "PR draft page must link back to the source issue.");
assertIncludes("← 추천 이슈", "PR draft page must link back to issue recommendations.");
assertIncludes("선택된 이슈", "PR draft page must show the selected issue card.");
assertIncludes("Base 브랜치", "PR draft page must show the base branch input.");
assertIncludes("Head 브랜치", "PR draft page must show the head branch input.");
assertIncludes("AI가 PR 초안 생성 중...", "PR draft page must show the generating state.");
assertIncludes("생성된 PR 초안", "PR draft page must show the generated result panel.");
assertIncludes("PR 제목", "PR draft page must show the draft title section.");
assertIncludes("PR 본문", "PR draft page must show the draft body section.");
assertIncludes("영어로 번역하기", "PR draft page must expose translation CTA.");
assertIncludes("PR 초안 작업 오류", "PR draft page must show draft/translation errors near the result panel.");
assertIncludes("draft && error", "PR draft page must keep translation failure feedback in the result context.");
assertIncludes("GitHub에서 PR 올리기 ↗", "PR draft page must expose GitHub submission link after translation.");
assertIncludes("상세 보기 (수정·삭제) →", "PR draft page must link to draft detail.");
assertIncludes("PR 초안 이력", "PR draft page must show draft history.");
assertIncludes("아직 생성된 PR 초안이 없습니다.", "PR draft page must show the empty history state.");

for (const text of [
  "defaultDraftInput",
  "spring-projects/spring-boot",
  "<textarea",
  "Repository",
  "Issue Number",
  "Recent drafts",
  "입력값을 채우고 초안을 생성하면",
  "buildDiffSeed",
  "diffContent: draftInput"
]) {
  assertNotIncludes(text, `PR draft page must not expose old "${text}" structure.`);
}

if (failures.length > 0) {
  console.error("PR draft page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("PR draft page parity validation passed.");
