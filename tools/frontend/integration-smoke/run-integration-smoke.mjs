import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { resolve } from "node:path";
import { createFrontendFixtureBackend } from "./fixture-backend.mjs";
import { launchChromeSession } from "../../extension/live-e2e/browser-session.mjs";

const repoRoot = resolve(import.meta.dirname, "..", "..", "..");
const timeoutMs = positiveInteger(process.env.FRONTEND_INTEGRATION_SMOKE_TIMEOUT_MS, 90_000);
const useLiveBackend = process.env.FRONTEND_INTEGRATION_USE_LIVE_BACKEND === "true";

const backend = useLiveBackend
  ? liveBackend()
  : await createFrontendFixtureBackend();
const frontendPort = await getAvailablePort();
const frontendOrigin = `http://127.0.0.1:${frontendPort}`;
const next = startNextServer(frontendPort, backend.origin);
let chrome;

try {
  await waitFor(async () => {
    const response = await fetch(`${frontendOrigin}/login`);
    return response.ok;
  }, "Next frontend did not become ready", timeoutMs);

  chrome = await launchChromeSession();
  await assertUnauthenticatedRedirect(chrome, frontendOrigin);

  const page = await chrome.newPage(`${frontendOrigin}/login`);
  await login(page);

  await visitAndAssert(page, `${frontendOrigin}/dashboard`, "Aquila Smoke");
  await visitAndAssert(page, `${frontendOrigin}/profile`, "TypeScript");
  await visitAndAssert(page, `${frontendOrigin}/issues`, "프론트 백엔드 통합 smoke");
  await visitAndAssert(page, `${frontendOrigin}/issues/issue-1`, "PR 초안 만들기");
  await visitAndAssert(
    page,
    `${frontendOrigin}/pr-draft?repo=AquilaXk%2Faquila-buddy&issueNumber=261&issueId=issue-1&base=main&head=test%2Ffront-backend-integration-smoke`,
    "PR 초안 생성"
  );
  await visitAndAssert(page, `${frontendOrigin}/pr/draft-1`, "close #261");

  if (!useLiveBackend) {
    backend.setProfileFailure(true);
    await visitAndAssert(page, `${frontendOrigin}/profile`, "프로필을 불러오지 못했습니다");
  }

  console.log("frontend backend integration smoke passed");
} finally {
  if (chrome) {
    await chrome.close();
  }
  await stopProcess(next);
  await backend.close();
}

async function assertUnauthenticatedRedirect(chromeSession, origin) {
  const page = await chromeSession.newPage(`${origin}/dashboard`);

  try {
    await waitFor(async () => {
      const currentUrl = await page.currentUrl();
      return currentUrl.includes("/login?next=");
    }, "Protected route did not redirect to login", 10_000);
  } finally {
    await page.close();
  }
}

async function login(page) {
  await page.evaluate('window.localStorage.setItem("aquila-buddy-token", "fixture-token")');
}

async function visitAndAssert(page, url, expectedText) {
  await page.navigate(url);
  try {
    await page.waitForText(expectedText);
  } catch (error) {
    const currentUrl = await page.currentUrl();
    const bodyText = await page.evaluate("document.body ? document.body.innerText : ''");
    throw new Error(
      `Expected text not found after navigating to ${url}. currentUrl=${currentUrl} expected=${expectedText} body=${bodyText}`,
      { cause: error }
    );
  }
}

function startNextServer(port, apiUrl) {
  const child = spawn(
    "npm",
    ["--prefix", "front", "run", "dev", "--", "--hostname", "127.0.0.1", "--port", String(port)],
    {
      cwd: repoRoot,
      detached: true,
      env: {
        ...process.env,
        NEXT_PUBLIC_API_URL: apiUrl,
        NEXT_TELEMETRY_DISABLED: "1",
      },
      stdio: ["ignore", "pipe", "pipe"],
    }
  );

  child.stdout.on("data", (chunk) => {
    if (process.env.FRONTEND_INTEGRATION_SMOKE_DEBUG === "true") {
      process.stdout.write(chunk);
    }
  });
  child.stderr.on("data", (chunk) => {
    if (process.env.FRONTEND_INTEGRATION_SMOKE_DEBUG === "true") {
      process.stderr.write(chunk);
    }
  });

  return child;
}

function liveBackend() {
  const origin = process.env.NEXT_PUBLIC_API_URL;
  if (!origin) {
    throw new Error("NEXT_PUBLIC_API_URL is required when FRONTEND_INTEGRATION_USE_LIVE_BACKEND=true.");
  }

  return {
    origin: origin.replace(/\/$/, ""),
    setProfileFailure() {},
    close: async () => {},
  };
}

async function stopProcess(child) {
  if (child.exitCode !== null || child.signalCode !== null) {
    return;
  }

  killProcessGroup(child, "SIGTERM");
  const exited = await new Promise((resolve) => {
    child.once("exit", resolve);
    setTimeout(() => resolve(false), 5_000);
  });
  if (exited === false && child.exitCode === null && child.signalCode === null) {
    killProcessGroup(child, "SIGKILL");
  }
}

function killProcessGroup(child, signal) {
  try {
    process.kill(-child.pid, signal);
  } catch {
    child.kill(signal);
  }
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
    throw new Error("Could not allocate a local frontend port");
  }
  return address.port;
}

async function waitFor(check, timeoutMessage, timeout = timeoutMs) {
  const deadline = Date.now() + timeout;
  let lastError;

  while (Date.now() < deadline) {
    try {
      if (await check()) {
        return;
      }
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`${timeoutMessage}${lastError ? ` (${lastError.message})` : ""}`);
}

function positiveInteger(value, fallback) {
  const parsed = Number.parseInt(value ?? "", 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}
