import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { describe, it } from "node:test";

const repoRoot = resolve(import.meta.dirname, "../..");
const ts = await import(pathToFileURL(join(repoRoot, "front/node_modules/typescript/lib/typescript.js")));

async function importTypeScriptModule(filePath) {
  const source = readFileSync(filePath, "utf8");
  const transpiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  });
  const encodedSource = Buffer.from(transpiled.outputText, "utf8").toString("base64");
  return import(`data:text/javascript;base64,${encodedSource}`);
}

const {
  bitapMatch,
  filterAndAggregateIssues,
} = await importTypeScriptModule(join(repoRoot, "front/src/lib/bitapSearch.ts"));

const {
  parseUnifiedDiff,
} = await importTypeScriptModule(join(repoRoot, "front/src/lib/parseUnifiedDiff.ts"));

const {
  buildSafeGitHubCompareUrl,
} = await importTypeScriptModule(join(repoRoot, "front/src/lib/githubQuickPullUrl.ts"));

describe("64-bit Bitap search and single-pass issue aggregation", () => {
  it("matches exact ASCII/Korean substrings and 1-edit typos", () => {
    assert.equal(bitapMatch("Improve pgvector HNSW search", "pgvector", 1), 2);
    assert.equal(bitapMatch("Improve pgvector HNSW search", "pgvectar", 1), 1);
    assert.equal(bitapMatch("Improve pgvector HNSW search", "pgvectr", 1), 1);
    assert.equal(bitapMatch("Improve pgvector HNSW search", "pgvectoer", 1), 1);
    assert.equal(bitapMatch("한국어 임베딩 검색 최적화", "임베딩 검색", 1), 2);
    assert.equal(bitapMatch("Improve UI button", "unrelated", 1), 0);
  });

  it("handles patterns exceeding 63 chars cleanly without bit overflow", () => {
    const longPattern = "alpha_".repeat(12); // 72 chars
    assert.equal(bitapMatch(`prefix ${longPattern} suffix`, longPattern, 1), 2);
    assert.equal(bitapMatch("prefix short suffix", longPattern, 1), 0);
  });

  it("aggregates repo counts, analyzed/pending tallies, and fuzzy ranking in 1 pass", () => {
    const issues = [
      {
        repoFullName: "owner/alpha",
        issueNumber: 12,
        title: "Fix SSE chunk framing in stream parser",
        labels: ["bug"],
        score: 0.91,
        isAnalyzed: true,
      },
      {
        repoFullName: "owner/alpha",
        issueNumber: 5,
        title: "Add unit test for straming reader",
        labels: ["test"],
        score: 0.72,
        isAnalyzed: false,
      },
      {
        repoFullName: "owner/beta",
        issueNumber: 3,
        title: "Update documentation index",
        labels: ["docs"],
        score: 0.95,
        isAnalyzed: true,
      },
    ];

    const allInAlpha = filterAndAggregateIssues(issues, "owner/alpha", "ALL", "", "ISSUE_ASC");
    assert.deepEqual(allInAlpha.repositoryOptions, [
      { repoFullName: "owner/alpha", count: 2 },
      { repoFullName: "owner/beta", count: 1 },
    ]);
    assert.equal(allInAlpha.repoScopedTotal, 2);
    assert.equal(allInAlpha.analyzedCount, 1);
    assert.equal(allInAlpha.pendingCount, 1);
    assert.deepEqual(allInAlpha.filtered.map((item) => item.issueNumber), [5, 12]);

    const fuzzyQuery = filterAndAggregateIssues(issues, "ALL", "ALL", "streaming", "SCORE");
    assert.equal(fuzzyQuery.filtered.length, 1);
    assert.equal(fuzzyQuery.fuzzyMatchedCount, 1);
    assert.equal(fuzzyQuery.filtered[0].issueNumber, 5);
  });
});

describe("1-pass Unified Diff parser", () => {
  it("parses multi-file unified diff with accurate line numbers and counts", () => {
    const rawDiff = [
      "diff --git a/src/a.ts b/src/a.ts",
      "index 111..222 100644",
      "--- a/src/a.ts",
      "+++ b/src/a.ts",
      "@@ -10,3 +10,4 @@ function demo() {",
      " const a = 1;",
      "-const b = 2;",
      "+const b = 3;",
      "+const c = 4;",
      " return a + b;",
      "diff --git a/src/old.ts b/src/new.ts",
      "--- a/src/old.ts",
      "+++ b/src/new.ts",
      "@@ -1,1 +1,1 @@",
      "-export const v = 1;",
      "+export const v = 2;",
    ].join("\n");

    const parsed = parseUnifiedDiff(rawDiff);
    assert.equal(parsed.truncated, false);
    assert.equal(parsed.files.length, 2);
    assert.equal(parsed.totalAdditions, 3);
    assert.equal(parsed.totalDeletions, 2);

    assert.equal(parsed.files[0].displayPath, "src/a.ts");
    assert.equal(parsed.files[0].additions, 2);
    assert.equal(parsed.files[0].deletions, 1);
    assert.deepEqual(parsed.files[0].hunks[0].lines[1], {
      type: "del",
      oldLine: 11,
      newLine: null,
      content: "const b = 2;",
    });
    assert.deepEqual(parsed.files[0].hunks[0].lines[2], {
      type: "add",
      oldLine: null,
      newLine: 11,
      content: "const b = 3;",
    });

    assert.equal(parsed.files[1].displayPath, "src/old.ts → src/new.ts");
  });

  it("enforces 64KB Fail-Closed truncation cap on oversized diffs", () => {
    const bigLine = `+${"x".repeat(200)}\n`;
    const bigDiff = `diff --git a/big.txt b/big.txt\n--- a/big.txt\n+++ b/big.txt\n@@ -1,1 +1,400 @@\n${bigLine.repeat(400)}`;
    const parsed = parseUnifiedDiff(bigDiff);
    assert.equal(parsed.truncated, true);
    assert.ok(parsed.totalAdditions > 0);
  });
});

describe("Safe GitHub Compare Quick-Pull URL builder", () => {
  it("preserves fork owner:branch delimiter while encoding special characters", () => {
    const built = buildSafeGitHubCompareUrl({
      upstreamRepo: "AquilaXk/aquila-buddy",
      baseBranch: "release/v1",
      headBranch: "fork-user:feat/my fix",
      title: "Fix query encoding",
      body: "close #1",
    });

    assert.equal(built.bodyTruncated, false);
    assert.ok(built.url.includes("/compare/release%2Fv1...fork-user:feat%2Fmy%20fix?quick_pull=1&expand=1"));
  });

  it("caps URL below 7,600 chars when PR body is huge", () => {
    const built = buildSafeGitHubCompareUrl({
      upstreamRepo: "AquilaXk/aquila-buddy",
      baseBranch: "main",
      headBranch: "feat/huge-body",
      title: "Large PR",
      body: "긴 설명 본문 ".repeat(2000),
    });

    assert.equal(built.bodyTruncated, true);
    assert.ok(built.url.length <= 7_600);
    assert.ok((new URL(built.url).searchParams.get("body") ?? "").includes("URL 길이 제한으로 일부 생략됨"));
  });

  it("rejects malformed or path-traversal upstreamRepo fail-closed", () => {
    for (const bad of ["octocat/repo/../../evil", "octocat/repo?foo=1", "./repo", "octocat/.."]) {
      assert.throws(() =>
        buildSafeGitHubCompareUrl({
          upstreamRepo: bad,
          baseBranch: "main",
          headBranch: "feat/x",
          title: "t",
          body: "b",
        })
      );
    }
  });
});
