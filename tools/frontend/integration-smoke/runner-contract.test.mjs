import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import test from "node:test";

const repoRoot = resolve(import.meta.dirname, "..", "..", "..");

test("frontend package exposes integration smoke script", async () => {
  const packageJson = JSON.parse(await readFile(resolve(repoRoot, "front/package.json"), "utf8"));

  assert.equal(
    packageJson.scripts["smoke:integration"],
    "node ../tools/frontend/integration-smoke/run-integration-smoke.mjs"
  );
});

test("integration smoke runner covers protected routes and failure envelope", async () => {
  const runner = await readFile(
    resolve(repoRoot, "tools/frontend/integration-smoke/run-integration-smoke.mjs"),
    "utf8"
  );

  for (const expected of [
    "/dashboard",
    "/profile",
    "/issues",
    "/issues/issue-1",
    "/pr-draft",
    "/pr/draft-1",
    "assertUnauthenticatedRedirect",
    "setProfileFailure(true)",
    "프로필을 불러오지 못했습니다",
  ]) {
    assert.match(runner, new RegExp(escapeRegExp(expected)));
  }
});

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
