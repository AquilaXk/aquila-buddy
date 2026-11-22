import assert from "node:assert/strict";
import { test } from "node:test";
import {
  buildChromeArgs,
  formatChromeStartupError,
  resolveChromeCandidates,
  resolveChromeUserDataDir,
  resolveChromeExtraFlags,
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

test("reads optional Chrome diagnostic flags from env", () => {
  assert.deepEqual(resolveChromeExtraFlags({}), []);
  assert.deepEqual(resolveChromeExtraFlags({ EXTENSION_E2E_CHROME_EXTRA_FLAGS: " --enable-logging=stderr   --v=1 " }), [
    "--enable-logging=stderr",
    "--v=1",
  ]);
});

test("reads optional Chrome user data dir for authenticated live smoke", () => {
  assert.equal(resolveChromeUserDataDir({}), null);
  assert.equal(
    resolveChromeUserDataDir({ EXTENSION_E2E_CHROME_USER_DATA_DIR: "/tmp/github-profile" }),
    "/tmp/github-profile"
  );
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

test("resolves Chrome binary candidates for macOS, Linux, and Windows", () => {
  const win = resolveChromeCandidates(
    {
      PROGRAMFILES: "C:\\Program Files",
      LOCALAPPDATA: "C:\\Users\\dev\\AppData\\Local",
    },
    "win32"
  );
  assert.ok(win.includes("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"));
  assert.ok(win.includes("C:\\Users\\dev\\AppData\\Local\\Google\\Chrome\\Application\\chrome.exe"));

  const linux = resolveChromeCandidates({}, "linux");
  assert.ok(linux.includes("/usr/bin/google-chrome"));

  const mac = resolveChromeCandidates({}, "darwin");
  assert.ok(mac.includes("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"));
});
