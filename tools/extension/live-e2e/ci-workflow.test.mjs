import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

test("extension live E2E job pins Chrome startup diagnostics", async () => {
  const workflow = await readFile(new URL("../../../.github/workflows/ci.yml", import.meta.url), "utf8");

  assert.match(workflow, /Run extension live E2E/);
  assert.match(workflow, /EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS: "30000"/);
  assert.match(workflow, /EXTENSION_E2E_CHROME_EXTRA_FLAGS: "--enable-logging=stderr --v=1"/);
});
