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

test("integration smoke runner supports release start mode", async () => {
  const runner = await readFile(
    resolve(repoRoot, "tools/frontend/integration-smoke/run-integration-smoke.mjs"),
    "utf8"
  );

  assert.match(runner, /FRONTEND_INTEGRATION_SERVER_MODE/);
  assert.match(runner, /serverMode === "start"/);
  assert.match(runner, /"start"/);
  assert.match(runner, /"dev"/);
});

test("integration smoke runner applies route wait timeout", async () => {
  const runner = await readFile(
    resolve(repoRoot, "tools/frontend/integration-smoke/run-integration-smoke.mjs"),
    "utf8"
  );
  const browserSession = await readFile(
    resolve(repoRoot, "tools/extension/live-e2e/browser-session.mjs"),
    "utf8"
  );

  assert.match(runner, /FRONTEND_INTEGRATION_ROUTE_TIMEOUT_MS/);
  assert.match(runner, /page\.waitForText\(expectedText, routeTimeoutMs\)/);
  assert.match(browserSession, /async waitForText\(text, timeoutMs\)/);
  assert.match(browserSession, /waitForFunction\([^)]*timeoutMs/s);
});

test("integration smoke runner reports route diagnostics", async () => {
  const runner = await readFile(
    resolve(repoRoot, "tools/frontend/integration-smoke/run-integration-smoke.mjs"),
    "utf8"
  );

  assert.match(runner, /serverMode=\$\{serverMode\}/);
  assert.match(runner, /routeTimeoutMs=\$\{routeTimeoutMs\}/);
  assert.match(runner, /document\.documentElement[\s\S]*outerHTML[\s\S]*slice\(0, 1200\)/);
  assert.match(runner, /html=\$\{htmlPreview\}/);
});

test("ci workflow runs frontend integration smoke", async () => {
  const workflow = await readFile(resolve(repoRoot, ".github/workflows/ci.yml"), "utf8");

  assert.match(workflow, /Run frontend integration smoke contracts/);
  assert.match(workflow, /node --test \.\.\/tools\/frontend\/integration-smoke\/\*\.test\.mjs/);
  assert.match(workflow, /Run frontend integration smoke/);
  assert.match(workflow, /FRONTEND_INTEGRATION_SERVER_MODE: start/);
  assert.match(workflow, /FRONTEND_INTEGRATION_ROUTE_TIMEOUT_MS: "20000"/);
  assert.match(workflow, /npm run smoke:integration/);
});

test("operations docs describe frontend integration smoke", async () => {
  const readme = await readFile(resolve(repoRoot, "README.md"), "utf8");
  const runbook = await readFile(resolve(repoRoot, "tools/smoke/AI_RUNTIME_SMOKE.md"), "utf8");

  for (const source of [readme, runbook]) {
    assert.match(source, /npm --prefix front run smoke:integration/);
    assert.match(source, /FRONTEND_INTEGRATION_SERVER_MODE=start/);
    assert.match(source, /FRONTEND_INTEGRATION_ROUTE_TIMEOUT_MS=20000/);
    assert.match(source, /FRONTEND_INTEGRATION_USE_LIVE_BACKEND=true/);
    assert.match(source, /NEXT_PUBLIC_API_URL=http:\/\/localhost:8080/);
    assert.match(source, /fixture backend/);
  }
});

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
