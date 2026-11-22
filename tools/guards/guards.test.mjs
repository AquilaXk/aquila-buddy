import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import test from "node:test";
import { checkPullRequest, findProhibited, parseNulLogOutput } from "./check-commit-messages.mjs";
import {
  findIntakeViolations,
  isDirectCliExecution,
  isForbiddenTrackedPath,
} from "./check-repo-hygiene.mjs";
import { prohibitedPatternsIn } from "./prohibited-terms.mjs";

const repoRoot = resolve(import.meta.dirname, "../..");
const decode = (b64) => Buffer.from(b64, "base64").toString("utf8");

test("prohibitedPatternsIn detects attribution headers and local-only doc names", () => {
  const coAuthorTrailer = `${decode("Q28tQXV0aG9yZWQtQnk6")} Example <dev@example.com>`;
  const localDocRef = `See ${decode("QUdFTlRTLm1k")} for details`;

  assert.equal(prohibitedPatternsIn("README.md", coAuthorTrailer).length, 1);
  assert.equal(prohibitedPatternsIn("README.md", localDocRef).length, 1);
  assert.deepEqual(prohibitedPatternsIn("README.md", "[Feat] 이슈 추천 필터 개선"), []);
});

test("parseNulLogOutput prevents RS control-char spoofing and validates full SHA headers", () => {
  const sha = "a".repeat(40);
  const spoofedBody = `[Fix] 요약\n\u001e${decode("Q28tQXV0aG9yZWQtQnk6")} Spoof <s@example.com>`;
  const records = parseNulLogOutput(`${sha}\n${spoofedBody}\0`);

  assert.equal(records.length, 1);
  assert.equal(records[0].sha, sha);
  assert.equal(checkPullRequest({ commits: records }).length, 1);
  assert.throws(() => parseNulLogOutput("not-a-valid-sha\nbody\0"), /Malformed git log record/);
});

test("isForbiddenTrackedPath blocks bare symlinks, case variants, and local editor dirs", () => {
  assert.equal(isForbiddenTrackedPath(decode("LmFnZW50cy8=").slice(0, -1)), true);
  assert.equal(isForbiddenTrackedPath("sub/.Agents"), true);
  assert.equal(isForbiddenTrackedPath(decode("QUdFTlRTLm1k").toLowerCase()), true);
  assert.equal(isForbiddenTrackedPath(".cursor/rules/foo.mdc"), true);
  assert.equal(isForbiddenTrackedPath(".aider.conf.yml"), true);
  assert.equal(isForbiddenTrackedPath(".playwright-mcp/trace.json"), true);
  assert.equal(
    isForbiddenTrackedPath("back/src/main/java/com/aquilabuddy/adapter/out/github/GitHubApiClient.java"),
    false
  );
});

test("checkPullRequest flags prohibited trailers in commits and PR title", () => {
  const badCommit = `[Fix] 버그 수정\n\n${decode("Q28tQXV0aG9yZWQtQnk6")} Bot <bot@example.com>`;
  const badTitle = `Update via ${decode("QW50aWdyYXZpdHk=")}`;

  const problems = checkPullRequest({
    commits: [
      { sha: "1234567890abcdef1234567890abcdef12345678", message: "[Feat] 정상 커밋 메시지" },
      { sha: "abcdef1234567890abcdef1234567890abcdef12", message: badCommit },
    ],
    title: badTitle,
  });

  assert.equal(problems.length, 2);
  assert.match(problems[0], /^commit abcdef12:/);
  assert.match(problems[1], /^PR title:/);
  assert.deepEqual(findProhibited("commit", "[Perf] pgvector HNSW 조회 최적화"), []);
});

test("findIntakeViolations allows bug-report form and config while blocking other templates", () => {
  assert.deepEqual(
    findIntakeViolations([
      ".github/ISSUE_TEMPLATE/bug_report.yml",
      ".github/ISSUE_TEMPLATE/config.yml",
      "CONTRIBUTING.md",
      "SECURITY.md",
    ]),
    []
  );

  const violations = findIntakeViolations([
    ".github/ISSUE_TEMPLATE/feature_request.yml",
    ".github/pull_request_template.md",
    "docs/pull_request_template/default.md",
    ".github/dependabot.yml",
    ".github/labels.yml",
  ]);
  assert.equal(violations.length, 5);

  const config = readFileSync(resolve(repoRoot, ".github/ISSUE_TEMPLATE/config.yml"), "utf8");
  const bugReport = readFileSync(resolve(repoRoot, ".github/ISSUE_TEMPLATE/bug_report.yml"), "utf8");
  assert.match(config, /^blank_issues_enabled:\s*false$/m);
  assert.match(bugReport, /^labels:\s*\["bug"\]$/m);
});

test("isDirectCliExecution resolves relative and platform paths via pathToFileURL", () => {
  assert.equal(isDirectCliExecution(import.meta.url, import.meta.filename), true);
  assert.equal(
    isDirectCliExecution(import.meta.url, "./tools/guards/guards.test.mjs"),
    true
  );
  assert.equal(isDirectCliExecution(import.meta.url, ""), false);
  assert.equal(isDirectCliExecution(import.meta.url, "./tools/guards/other.mjs"), false);
});

test("repository hygiene guard passes on current workspace including adapter/out sources", () => {
  const output = execFileSync(process.execPath, ["tools/guards/check-repo-hygiene.mjs"], {
    cwd: repoRoot,
    encoding: "utf8",
  });
  assert.match(output, /Repository hygiene check passed/);
});
