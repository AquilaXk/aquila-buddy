import assert from "node:assert/strict";
import { test } from "node:test";
import {
  buildChromeArgs,
  formatChromeStartupError,
  resolveChromeStartupTimeoutMs,
} from "./browser-session.mjs";

test("uses a CI-safe Chrome startup timeout with optional env override", () => {
  assert.equal(resolveChromeStartupTimeoutMs({}), 30_000);
  assert.equal(resolveChromeStartupTimeoutMs({ EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS: "45000" }), 45_000);
  assert.equal(resolveChromeStartupTimeoutMs({ EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS: "0" }), 30_000);
  assert.equal(resolveChromeStartupTimeoutMs({ EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS: "invalid" }), 30_000);
});

test("builds Chrome arguments with an explicit loopback DevTools address", () => {
  const args = buildChromeArgs({
    remoteDebuggingPort: 9222,
    userDataDir: "/tmp/aquila-profile",
    extraFlags: ["--enable-logging=stderr"],
  });

  assert.ok(args.includes("--remote-debugging-address=127.0.0.1"));
  assert.ok(args.includes("--remote-debugging-port=9222"));
  assert.ok(args.includes("--user-data-dir=/tmp/aquila-profile"));
  assert.ok(args.includes("--enable-logging=stderr"));
});

test("formats Chrome startup failures with process and stderr diagnostics", () => {
  const message = formatChromeStartupError({
    timeoutMessage: "Chrome DevTools endpoint did not open",
    lastError: new Error("fetch failed"),
    exitState: { exited: true, code: 1, signal: null },
    stderr: ["zygote failed"],
  });

  assert.match(message, /Chrome DevTools endpoint did not open/);
  assert.match(message, /fetch failed/);
  assert.match(message, /exitCode=1/);
  assert.match(message, /zygote failed/);
});
