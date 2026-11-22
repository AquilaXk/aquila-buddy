import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { resolve } from "node:path";
import test from "node:test";
import { promisify } from "node:util";

const repoRoot = resolve(import.meta.dirname, "../..");
const execFileAsync = promisify(execFile);

test("ci workflow detects changed paths before heavy jobs", async () => {
  const workflow = await readWorkflow();
  const changes = jobBlock(workflow, "changes");

  assert.match(changes, /name: Changes/);
  assert.match(changes, /fetch-depth: 0/);
  assert.match(changes, /id: filter/);
  assert.match(changes, /git diff --name-only/);
  assert.match(changes, /bash tools\/ci\/detect-changed-paths\.sh changed-files\.txt/);

  for (const output of [
    "backend",
    "frontend",
    "extension",
    "pgvector",
    "operations",
    "docs_only",
    "ci",
  ]) {
    assert.match(changes, new RegExp(`${output}: \\$\\{\\{ steps\\.filter\\.outputs\\.${output} \\}\\}`));
  }
});

test("heavy ci jobs are gated by changed path outputs", async () => {
  const workflow = await readWorkflow();

  assertJobGate(workflow, "backend", "backend");
  assertJobGate(workflow, "frontend", "frontend");
  assertJobGate(workflow, "extension", "extension");
  assertJobGate(workflow, "pgvector-integration", "pgvector");
  assertJobGate(workflow, "operations-smoke", "operations");
});

test("docs-only changes run lightweight documentation contracts", async () => {
  const workflow = await readWorkflow();
  const docsValidation = jobBlock(workflow, "docs-validation");

  assert.match(docsValidation, /name: Docs Validation/);
  assert.match(docsValidation, /needs: changes/);
  assert.match(
    docsValidation,
    /if: \$\{\{ needs\.changes\.outputs\.docs_only == 'true' \|\| needs\.changes\.outputs\.ci == 'true' \}\}/
  );
  assert.match(docsValidation, /git diff --check/);
  assert.match(docsValidation, /node --test tools\/ci\/\*\.test\.mjs/);
  assert.match(docsValidation, /node --test tools\/frontend\/integration-smoke\/runner-contract\.test\.mjs/);
});

test("documentation contracts preserve runtime smoke README expectations", async () => {
  const readme = await readFile(resolve(repoRoot, "README.md"), "utf8");
  const runbook = await readFile(resolve(repoRoot, "tools/smoke/AI_RUNTIME_SMOKE.md"), "utf8");

  assert.match(readme, /tools\/smoke\/AI_RUNTIME_SMOKE\.md/);
  assert.match(readme, /CONTRIBUTING\.md/);
  assert.match(readme, /SECURITY\.md/);
  assert.match(readme, /THIRD_PARTY_NOTICES\.md/);

  assert.match(runbook, /기본 운영 AI provider는 `vertex-ai`입니다\./);
  assert.match(runbook, /`AI_PROVIDER=openai-compatible`: OpenAI-compatible chat completion\/embedding provider를 사용합니다\./);
  assert.match(runbook, /FRONTEND_INTEGRATION_SERVER_MODE=start/);
  assert.match(runbook, /FRONTEND_INTEGRATION_USE_LIVE_BACKEND=true/);
  assert.match(runbook, /Langfuse tracing은 선택 기능/);
});

test("path classifier treats README and LICENSE changes as docs-only", async () => {
  const outputs = await classifyChangedFiles(["README.md", "LICENSE"]);

  assert.equal(outputs.docs_only, "true");
  assert.equal(outputs.backend, "false");
  assert.equal(outputs.frontend, "false");
  assert.equal(outputs.extension, "false");
  assert.equal(outputs.pgvector, "false");
  assert.equal(outputs.operations, "false");
  assert.equal(outputs.ci, "false");
});

test("path classifier enables backend and pgvector checks for backend changes", async () => {
  const outputs = await classifyChangedFiles(["back/src/main/java/com/aquilabuddy/AquilaBuddyApplication.java"]);

  assert.equal(outputs.docs_only, "false");
  assert.equal(outputs.backend, "true");
  assert.equal(outputs.pgvector, "true");
  assert.equal(outputs.frontend, "false");
  assert.equal(outputs.extension, "false");
});

test("path classifier enables all heavy checks for ci workflow changes", async () => {
  const outputs = await classifyChangedFiles([".github/workflows/ci.yml"]);

  assert.equal(outputs.docs_only, "false");
  assert.equal(outputs.ci, "true");
  assert.equal(outputs.backend, "true");
  assert.equal(outputs.frontend, "true");
  assert.equal(outputs.extension, "true");
  assert.equal(outputs.pgvector, "true");
  assert.equal(outputs.operations, "true");
});

test("path classifier treats guard updates as ci gate changes", async () => {
  const outputs = await classifyChangedFiles(["tools/guards/check-repo-hygiene.mjs"]);

  assert.equal(outputs.docs_only, "false");
  assert.equal(outputs.ci, "true");
  assert.equal(outputs.backend, "true");
  assert.equal(outputs.frontend, "true");
  assert.equal(outputs.extension, "true");
});

test("path classifier routes release and desktop packaging tools to operations checks", async () => {
  const outputs = await classifyChangedFiles(["tools/release/release-assets.mjs"]);

  assert.equal(outputs.docs_only, "false");
  assert.equal(outputs.operations, "true");
});

async function readWorkflow() {
  return readFile(resolve(repoRoot, ".github/workflows/ci.yml"), "utf8");
}

function assertJobGate(workflow, jobId, outputName) {
  const block = jobBlock(workflow, jobId);

  assert.match(block, /needs: changes/);
  assert.match(block, new RegExp(`if: \\$\\{\\{ needs\\.changes\\.outputs\\.${outputName} == 'true' \\}\\}`));
}

function jobBlock(workflow, jobId) {
  const expression = new RegExp(`\\n  ${escapeRegExp(jobId)}:\\n([\\s\\S]*?)(?=\\n  [a-zA-Z0-9_-]+:\\n|\\n*$)`);
  const match = workflow.match(expression);

  assert.ok(match, `Expected job '${jobId}' to exist`);

  return match[1];
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function classifyChangedFiles(files) {
  const directory = await mkdtemp(resolve(tmpdir(), "aquila-ci-paths-"));
  const changedFilesPath = resolve(directory, "changed-files.txt");
  const outputPath = resolve(directory, "github-output.txt");

  await writeFile(changedFilesPath, `${files.join("\n")}\n`);
  await execFileAsync("bash", [resolve(repoRoot, "tools/ci/detect-changed-paths.sh"), changedFilesPath], {
    env: {
      ...process.env,
      GITHUB_OUTPUT: outputPath,
      GITHUB_STEP_SUMMARY: resolve(directory, "summary.md"),
    },
  });

  const rawOutput = await readFile(outputPath, "utf8");

  return Object.fromEntries(
    rawOutput
      .trim()
      .split("\n")
      .filter(Boolean)
      .map((line) => line.split("=", 2))
  );
}
