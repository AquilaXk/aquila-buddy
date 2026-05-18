import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

test("extension live E2E job pins Chrome startup diagnostics", async () => {
  const workflow = await readFile(new URL("../../../.github/workflows/ci.yml", import.meta.url), "utf8");

  assert.match(workflow, /Run extension live E2E contracts/);
  assert.match(workflow, /npm --prefix extension run test:e2e:contracts/);
  assert.match(workflow, /Run extension live E2E/);
  assert.match(workflow, /EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS: "30000"/);
  assert.match(workflow, /EXTENSION_E2E_CHROME_EXTRA_FLAGS: "--enable-logging=stderr --v=1"/);
});

test("extension GitHub live smoke workflow is manual opt-in and uploads evidence", async () => {
  const workflow = await readFile(
    new URL("../../../.github/workflows/extension-github-live-smoke.yml", import.meta.url),
    "utf8"
  );

  assert.match(workflow, /workflow_dispatch:/);
  assert.doesNotMatch(workflow, /\n  pull_request:/);
  assert.doesNotMatch(workflow, /\n  push:/);
  assert.match(workflow, /EXTENSION_GITHUB_LIVE_SMOKE_ENABLED: \$\{\{ inputs.enabled \}\}/);
  assert.match(workflow, /EXTENSION_GITHUB_LIVE_SKIP_ON_MISSING_SECRETS: "true"/);
  assert.match(workflow, /EXTENSION_GITHUB_LIVE_EVIDENCE_FILE: tools\/smoke\/release-readiness\/extension-github-live-\$\{\{ github.run_id \}\}\.md/);
  assert.match(workflow, /npm --prefix extension run test:e2e:github-live/);
  assert.match(workflow, /uses: actions\/upload-artifact@v4/);
  assert.match(workflow, /extension-github-live-smoke-evidence/);
});
