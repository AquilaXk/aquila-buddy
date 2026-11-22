import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const callbackPagePath = join(repoRoot, "front/src/app/oauth/callback/page.tsx");
const callbackPage = readFileSync(callbackPagePath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!callbackPage.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (callbackPage.includes(needle)) {
    fail(message);
  }
}

assertIncludes("\"use client\";", "OAuth callback page must stay client-rendered.");
assertIncludes("readOAuthCallbackParams", "OAuth callback page must keep normalized callback parsing.");
assertIncludes("markLoggedIn()", "OAuth callback page must mark the cookie-backed session.");
assertIncludes("router.replace(callbackParams.redirectTo)", "OAuth callback page must redirect to the parsed target.");
assertIncludes("callbackParams.error", "OAuth callback page must handle provider error query fixtures.");
assertIncludes("const [error, setError] = useState<string | null>(null)", "OAuth callback page must keep a compact error state.");
assertIncludes("로그인 처리 중...", "OAuth callback page must show the processing label.");
assertIncludes("animate-spin", "OAuth callback page must use the compact spinner.");
assertIncludes("로그인 실패", "OAuth callback page must show the compact failure title.");
assertIncludes("다시 로그인", "OAuth callback page must keep the login retry link.");
assertNotIncludes("github-", "OAuth callback page must not use duplicate github-* color classes.");
assertNotIncludes("setToken(callbackParams.token)", "OAuth callback page must not store OAuth JWTs in localStorage.");
assertNotIncludes("!callbackParams.token", "OAuth callback page must accept cookie-backed callbacks without a token query.");
assertNotIncludes("토큰을 받지 못했습니다.", "OAuth callback page must not require a URL token.");

for (const text of [
  "CallbackStatus",
  "getCallbackContent",
  "로그인이 완료되었습니다.",
  "GitHub OAuth",
  "계속하기",
  "OAuth Callback",
  "window.setTimeout",
  "status !== \"stored\"",
  "panelClassName",
  "messageClassName"
]) {
  assertNotIncludes(text, `OAuth callback page must not expose old "${text}" structure.`);
}

if (failures.length > 0) {
  console.error("OAuth callback parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

const fixtureResult = spawnSync(process.execPath, ["--test", "tools/frontend/oauth-callback-fixtures.test.mjs"], {
  cwd: repoRoot,
  encoding: "utf8",
});

if (fixtureResult.stdout) {
  process.stdout.write(fixtureResult.stdout);
}
if (fixtureResult.stderr) {
  process.stderr.write(fixtureResult.stderr);
}
if (fixtureResult.status !== 0) {
  console.error("OAuth callback fixture validation failed.");
  process.exit(1);
}

console.log("OAuth callback parity validation passed.");
