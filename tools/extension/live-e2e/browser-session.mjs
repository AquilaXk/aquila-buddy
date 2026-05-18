import { mkdtemp, rm, readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { createServer } from "node:net";
import { tmpdir } from "node:os";
import { dirname, resolve, join } from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..", "..");
const contentScriptPath = resolve(repoRoot, "extension", "content.js");
const contentStylePath = resolve(repoRoot, "extension", "content.css");

export async function launchChromeSession() {
  const executable = findChromeExecutable();
  const remoteDebuggingPort = await getAvailablePort();
  const userDataDir = await mkdtemp(join(tmpdir(), "aquila-extension-e2e-"));
  const stderr = [];
  const exitState = { exited: false, code: null, signal: null };

  const chrome = spawn(executable, buildChromeArgs({
    remoteDebuggingPort,
    userDataDir,
    extraFlags: resolveChromeExtraFlags(),
  }), {
    stdio: ["ignore", "ignore", "pipe"],
  });

  chrome.stderr.on("data", (chunk) => {
    stderr.push(chunk.toString());
  });
  chrome.once("exit", (code, signal) => {
    exitState.exited = true;
    exitState.code = code;
    exitState.signal = signal;
  });

  try {
    await waitFor(async () => {
      const response = await fetch(`http://127.0.0.1:${remoteDebuggingPort}/json/version`);
      return response.ok;
    }, (lastError) => formatChromeStartupError({
      timeoutMessage: "Chrome DevTools endpoint did not open",
      lastError,
      exitState,
      stderr,
    }), resolveChromeStartupTimeoutMs());
  } catch (error) {
    await closeChromeProcess(chrome, exitState);
    await rm(userDataDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    throw error;
  }

  return {
    async newPage(url) {
      const target = await createTarget(remoteDebuggingPort, url);
      const client = await createCdpClient(target.webSocketDebuggerUrl);
      await client.send("Page.enable");
      await client.send("Runtime.enable");
      await waitForLoad(client);
      return createPage(client);
    },
    async close() {
      await closeChromeProcess(chrome, exitState);
      await rm(userDataDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 });
    },
  };
}

export function buildChromeArgs({ remoteDebuggingPort, userDataDir, extraFlags = [] }) {
  return [
    "--headless=new",
    "--disable-gpu",
    "--disable-dev-shm-usage",
    "--no-first-run",
    "--no-default-browser-check",
    "--no-sandbox",
    "--remote-debugging-address=127.0.0.1",
    `--remote-debugging-port=${remoteDebuggingPort}`,
    `--user-data-dir=${userDataDir}`,
    ...extraFlags,
    "about:blank",
  ];
}

export function resolveChromeStartupTimeoutMs(env = process.env) {
  const value = Number.parseInt(env.EXTENSION_E2E_CHROME_STARTUP_TIMEOUT_MS ?? "", 10);
  return Number.isInteger(value) && value > 0 ? value : 30_000;
}

export function resolveChromeExtraFlags(env = process.env) {
  return (env.EXTENSION_E2E_CHROME_EXTRA_FLAGS ?? "")
    .split(/\s+/)
    .map((flag) => flag.trim())
    .filter(Boolean);
}

export function formatChromeStartupError({ timeoutMessage, lastError, exitState, stderr }) {
  const diagnostics = [
    timeoutMessage,
    lastError ? `lastError=${lastError.message}` : "",
    exitState?.exited ? `exitCode=${exitState.code ?? "null"}` : "exitCode=running",
    exitState?.signal ? `signal=${exitState.signal}` : "",
    stderr?.length ? `stderr=${stderr.join("").trim()}` : "",
  ].filter(Boolean);
  return diagnostics.join(" ");
}

async function closeChromeProcess(chrome, exitState) {
  if (exitState.exited) {
    return;
  }
  chrome.kill("SIGTERM");
  await new Promise((resolve) => chrome.once("exit", resolve));
}

function findChromeExecutable() {
  const candidates = [
    process.env.CHROME_BIN,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].filter(Boolean);

  const executable = candidates.find((candidate) => existsSync(candidate));
  if (!executable) {
    throw new Error(`Chrome executable not found. Set CHROME_BIN to run extension E2E tests.`);
  }
  return executable;
}

async function getAvailablePort() {
  const server = createServer();
  await new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolve);
  });
  const address = server.address();
  await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
  if (!address || typeof address === "string") {
    throw new Error("Could not allocate a local port for Chrome DevTools");
  }
  return address.port;
}

async function createTarget(port, url) {
  const endpoint = `http://127.0.0.1:${port}/json/new?${encodeURIComponent(url)}`;
  const response = await fetch(endpoint, { method: "PUT" });
  if (!response.ok) {
    throw new Error(`Could not create Chrome target: HTTP ${response.status}`);
  }
  return response.json();
}

async function createCdpClient(webSocketDebuggerUrl) {
  const ws = new WebSocket(webSocketDebuggerUrl);
  const pending = new Map();
  const eventWaiters = new Map();
  let nextId = 1;

  await new Promise((resolve, reject) => {
    ws.addEventListener("open", resolve, { once: true });
    ws.addEventListener("error", reject, { once: true });
  });

  ws.addEventListener("message", (event) => {
    const message = JSON.parse(event.data);
    if (message.id && pending.has(message.id)) {
      const { resolve: resolvePending, reject } = pending.get(message.id);
      pending.delete(message.id);
      if (message.error) {
        reject(new Error(message.error.message));
      } else {
        resolvePending(message.result);
      }
      return;
    }

    const waiters = eventWaiters.get(message.method);
    if (waiters?.length) {
      waiters.splice(0).forEach((resolveEvent) => resolveEvent(message.params));
    }
  });

  return {
    send(method, params = {}) {
      const id = nextId++;
      ws.send(JSON.stringify({ id, method, params }));
      return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
    },
    waitForEvent(method) {
      return new Promise((resolve) => {
        const waiters = eventWaiters.get(method) ?? [];
        waiters.push(resolve);
        eventWaiters.set(method, waiters);
      });
    },
    close() {
      ws.close();
    },
  };
}

function createPage(client) {
  return {
    async installContentScript(fixtures) {
      const [contentScript, contentStyle] = await Promise.all([
        readFile(contentScriptPath, "utf8"),
        readFile(contentStylePath, "utf8"),
      ]);

      await evaluate(client, `
        window.__omosFixtures = ${JSON.stringify(fixtures)};
        window.__omosMessages = [];
        window.chrome = {
          runtime: {
            sendMessage(message, callback) {
              window.__omosMessages.push(message);
              const response = resolveOmosMessage(message, window.__omosFixtures);
              queueMicrotask(() => callback(response));
            },
            onMessage: {
              addListener(listener) {
                window.__omosRuntimeListener = listener;
              }
            }
          }
        };
        function resolveOmosMessage(message, fixtures) {
          if (message.type === "GET_CACHED_RECOMMENDATIONS") {
            return { ok: true, data: fixtures.cachedRecommendations ?? null };
          }
          if (message.type === "GET_ISSUE_GUIDE") {
            return { ok: true, data: fixtures.issueGuide ?? null };
          }
          if (message.type === "GET_ISSUE_PSEUDO") {
            return { ok: true, data: fixtures.issuePseudo ?? null };
          }
          if (message.type === "CREATE_PR_DRAFT") {
            return { ok: true, data: fixtures.prDraft ?? null };
          }
          if (message.type === "TRANSLATE_PR_DRAFT") {
            return { ok: true, data: fixtures.prTranslation ?? null };
          }
          return { ok: false, error: "UNHANDLED_TEST_MESSAGE_" + message.type };
        }
      `);

      await evaluate(client, `
        const style = document.createElement("style");
        style.textContent = ${JSON.stringify(contentStyle)};
        document.head.appendChild(style);
      `);
      await evaluate(client, contentScript);
    },
    async click(selector) {
      await waitForFunction(client, `Boolean(document.querySelector(${JSON.stringify(selector)}))`);
      await evaluate(client, `document.querySelector(${JSON.stringify(selector)}).click()`);
    },
    async textContent(selector) {
      await waitForFunction(client, `Boolean(document.querySelector(${JSON.stringify(selector)}))`);
      return evaluate(client, `document.querySelector(${JSON.stringify(selector)}).textContent`);
    },
    async fill(selector, value) {
      await waitForFunction(client, `Boolean(document.querySelector(${JSON.stringify(selector)}))`);
      await evaluate(client, `
        {
          const input = document.querySelector(${JSON.stringify(selector)});
          input.value = ${JSON.stringify(value)};
          input.dispatchEvent(new Event("input", { bubbles: true }));
          input.dispatchEvent(new Event("change", { bubbles: true }));
        }
      `);
    },
    async inputValue(selector) {
      await waitForFunction(client, `Boolean(document.querySelector(${JSON.stringify(selector)}))`);
      return evaluate(client, `document.querySelector(${JSON.stringify(selector)}).value`);
    },
    async evaluate(expression) {
      return evaluate(client, expression);
    },
    async navigate(url) {
      const loadEvent = client.waitForEvent("Page.loadEventFired");
      await client.send("Page.navigate", { url });
      await Promise.race([
        loadEvent,
        new Promise((resolve) => setTimeout(resolve, 1_000)),
      ]);
    },
    async waitForText(text, timeoutMs) {
      await waitForFunction(
        client,
        `Boolean(document.body && document.body.innerText.includes(${JSON.stringify(text)}))`,
        timeoutMs
      );
    },
    async currentUrl() {
      return evaluate(client, "window.location.href");
    },
    async close() {
      client.close();
    },
  };
}

async function evaluate(client, expression) {
  const result = await client.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text);
  }
  return result.result?.value;
}

async function waitForFunction(client, expression, timeoutMs) {
  await waitFor(async () => evaluate(client, expression), `Timed out waiting for ${expression}`, timeoutMs);
}

async function waitForLoad(client) {
  const loadEvent = client.waitForEvent("Page.loadEventFired");
  await Promise.race([
    loadEvent,
    new Promise((resolve) => setTimeout(resolve, 1_000)),
  ]);
}

async function waitFor(check, timeoutMessage, timeoutMs = 10_000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      if (await check()) {
        return;
      }
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  const message = typeof timeoutMessage === "function"
    ? timeoutMessage(lastError)
    : `${timeoutMessage}${lastError ? ` (${lastError.message})` : ""}`;
  throw new Error(message);
}
