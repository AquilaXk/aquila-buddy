import assert from "node:assert/strict";
import { readFile, mkdtemp } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";
import { test } from "node:test";

test("extension package exposes GitHub live smoke contract scripts", async () => {
  const packageJson = JSON.parse(
    await readFile(new URL("../../../extension/package.json", import.meta.url), "utf8")
  );

  assert.equal(packageJson.scripts["test:e2e:contracts"], "node --test ../tools/extension/live-e2e/*.test.mjs");
  assert.equal(packageJson.scripts["test:e2e:github-live"], "node ../tools/extension/live-e2e/run-github-live-smoke.mjs");
});

test("GitHub live smoke skips with evidence when disabled", async () => {
  const tempDir = await mkdtemp(join(tmpdir(), "extension-github-live-"));
  const evidenceFile = join(tempDir, "evidence.md");

  const result = await runGitHubLiveSmoke({
    EXTENSION_GITHUB_LIVE_SMOKE_ENABLED: "false",
    EXTENSION_GITHUB_LIVE_EVIDENCE_FILE: evidenceFile,
  });

  assert.equal(result.exitCode, 0);
  assert.match(result.output, /Extension GitHub live smoke skipped because EXTENSION_GITHUB_LIVE_SMOKE_ENABLED is not true/);
  const evidence = await readFile(evidenceFile, "utf8");
  assert.match(evidence, /# Extension GitHub Live Smoke Evidence/);
  assert.match(evidence, /- result: skipped/);
  assert.match(evidence, /\| badge \| skipped \|/);
});

test("GitHub live smoke skips with evidence when profile or target env is missing", async () => {
  const tempDir = await mkdtemp(join(tmpdir(), "extension-github-live-"));
  const evidenceFile = join(tempDir, "evidence.md");

  const result = await runGitHubLiveSmoke({
    EXTENSION_GITHUB_LIVE_SMOKE_ENABLED: "true",
    EXTENSION_GITHUB_LIVE_SKIP_ON_MISSING_SECRETS: "true",
    EXTENSION_GITHUB_LIVE_EVIDENCE_FILE: evidenceFile,
    EXTENSION_GITHUB_LIVE_REPO: "AquilaXk/aquila-buddy",
    EXTENSION_GITHUB_LIVE_ISSUE_NUMBER: "42",
  });

  assert.equal(result.exitCode, 0);
  assert.match(result.output, /Extension GitHub live smoke skipped because required profile or target env is missing/);
  const evidence = await readFile(evidenceFile, "utf8");
  assert.match(evidence, /- result: skipped/);
  assert.match(evidence, /missing required profile or target env/);
  assert.match(evidence, /\| pr-autofill \| skipped \|/);
});

async function runGitHubLiveSmoke(env) {
  const child = spawn(process.execPath, ["../tools/extension/live-e2e/run-github-live-smoke.mjs"], {
    cwd: new URL("../../../extension", import.meta.url),
    env: {
      PATH: process.env.PATH,
      ...env,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  const output = await new Promise((resolve) => {
    const chunks = [];
    child.stdout.on("data", (chunk) => chunks.push(chunk));
    child.stderr.on("data", (chunk) => chunks.push(chunk));
    child.on("close", () => resolve(Buffer.concat(chunks).toString("utf8")));
  });
  return {
    exitCode: child.exitCode,
    output,
  };
}
