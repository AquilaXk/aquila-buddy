import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const apiClientPath = join(repoRoot, "front/src/lib/api.ts");
const prDraftRequestPath = join(repoRoot, "back/src/main/java/com/aquilabuddy/domain/prdraft/PullRequestDraftRequest.java");
const prDraftServicePath = join(repoRoot, "back/src/main/java/com/aquilabuddy/application/service/prdraft/PullRequestDraftService.java");
const prDraftCreatePagePath = join(repoRoot, "front/src/app/pr-draft/page.tsx");
const prDetailPagePath = join(repoRoot, "front/src/app/pr/[id]/page.tsx");
const readinessSectionPath = join(repoRoot, "front/src/components/PrDraftReadinessSection.tsx");
const apiClient = readFileSync(apiClientPath, "utf8");
const prDraftRequest = readFileSync(prDraftRequestPath, "utf8");
const prDraftService = readFileSync(prDraftServicePath, "utf8");
const prDraftCreatePage = readFileSync(prDraftCreatePagePath, "utf8");
const prDetailPage = readFileSync(prDetailPagePath, "utf8");
const readinessSection = readFileSync(readinessSectionPath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!prDetailPage.includes(needle)) {
    fail(message);
  }
}

function assertFileIncludes(content, needle, message) {
  if (!content.includes(needle)) {
    fail(message);
  }
}

function assertFileNotIncludes(content, needle, message) {
  if (content.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (prDetailPage.includes(needle)) {
    fail(message);
  }
}

assertIncludes("\"use client\";", "PR detail page must be a client screen.");
assertIncludes("useParams", "PR detail page must read the route id on the client.");
assertIncludes("useRouter", "PR detail page must navigate after deletion.");
assertIncludes("getPullRequestDraft", "PR detail page must fetch the draft.");
assertIncludes("updatePullRequestDraft", "PR detail page must update the draft.");
assertIncludes("deletePullRequestDraft", "PR detail page must delete the draft.");
assertIncludes("translatePullRequestDraft", "PR detail page must translate the draft.");
assertIncludes("@/lib/format", "PR detail page must import shared formatDate from @/lib/format.");
assertIncludes("PR 초안을 찾을 수 없습니다.", "PR detail page must keep the not-found state.");
assertIncludes("PR 초안 목록", "PR detail page must link back to draft list.");
assertIncludes("연결된 이슈", "PR detail page must show connected issue context.");
assertIncludes("{draft.baseBranch} → {draft.headBranch}", "PR detail page must use typographic arrow → for branch display.");
assertNotIncludes("-&gt;", "PR detail page must not use ASCII -> arrow for branch display.");

assertFileIncludes(readinessSection, "PR 기여 준비도 점검", "PrDraftReadinessSection must use clean heading 'PR 기여 준비도 점검'.");
assertFileNotIncludes(readinessSection, "7대 기여 준비도", "PrDraftReadinessSection must not use marketing slop '7대 기여 준비도'.");

assertFileIncludes(apiClient, "sourceIssueId?: string;", "PR draft create API request must accept sourceIssueId.");
assertFileIncludes(apiClient, "sourceIssueId: string | null;", "Stored PR draft API type must expose sourceIssueId.");
assertFileIncludes(apiClient, "type PullRequestDraftReadiness = {\n  draftId: string;\n  sourceIssueId: string | null;", "PR draft readiness API type must expose sourceIssueId.");
assertFileIncludes(apiClient, "type PullRequestDraftTranslateResult = {\n  titleEn: string;\n  bodyEn: string;\n  sourceIssueId: string | null;", "PR draft translate result API type must expose sourceIssueId.");
assertFileIncludes(apiClient, "type PullRequestDraftTranslation = PullRequestDraft & {\n  id: string;\n  draftId: string;\n  sourceIssueId: string | null;", "PR draft translation API type must expose sourceIssueId.");
assertFileIncludes(prDraftCreatePage, "sourceIssueId: draftInput.issueId || undefined", "PR draft create payload must send sourceIssueId when present.");
assertFileIncludes(prDraftRequest, "hasSourceIssueId", "PR draft request must expose sourceIssueId presence check.");
assertFileIncludes(prDraftService, "resolveSourceIssueIdFallback", "PR draft service must isolate sourceIssueId fallback logic.");
assertIncludes("draft.sourceIssueId", "PR detail page must branch on stored sourceIssueId.");
assertIncludes("원본 이슈 보기", "PR detail page must link to the source issue detail.");
assertIncludes("PR 제목", "PR detail page must show the title card.");
assertIncludes("PR 본문", "PR detail page must show the body card.");
assertIncludes("코드 변경 내용 (diff)", "PR detail page must show the diff card.");
assertIncludes("draft.diffContent", "PR detail page must render stored diff content.");
assertIncludes("편집", "PR detail page must expose edit action.");
assertIncludes("삭제", "PR detail page must expose delete action.");
assertIncludes("router.replace(\"/pr-draft\")", "PR detail delete must replace history with the draft list.");
assertIncludes("저장", "PR detail page must expose save action.");
assertIncludes("취소", "PR detail page must expose cancel action.");
assertIncludes("복사됨 ✓", "PR detail page must show copy feedback.");
assertIncludes("영어로 번역하기", "PR detail page must expose translation CTA.");
assertIncludes("영어 번역이 완료되었습니다.", "PR detail page must show translated state.");
assertIncludes("GitHub에서 PR 올리기 ↗", "PR detail page must expose GitHub submission link.");

for (const text of [
  "PrDraftContributionReportPanel",
  "PrDraftReadinessPanel",
  "PrDraftTimelinePanel",
  "PrDraftSubmissionPanel",
  "TranslationPanel",
  "loadPrDetailOverview",
  "Draft ID",
  "Repository",
  "Created"
]) {
  assertNotIncludes(text, `PR detail page must not expose old "${text}" structure.`);
}

assertNotIncludes("router.push(\"/pr-draft\")", "PR detail delete must not leave a deleted draft detail in browser history.");

for (const text of [">Branch<", "{'Branch'}", '{"Branch"}']) {
  assertNotIncludes(text, "PR detail page must not expose the old Branch label.");
}

if (failures.length > 0) {
  console.error("PR detail page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("PR detail page parity validation passed.");
