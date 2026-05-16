import { readFileSync } from "node:fs";
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
assertIncludes("setToken(callbackParams.token)", "OAuth callback page must store the callback token.");
assertIncludes("router.replace(callbackParams.redirectTo)", "OAuth callback page must redirect to the parsed target.");
assertIncludes("const [error, setError] = useState<string | null>(null)", "OAuth callback page must keep a compact error state.");
assertIncludes("로그인 처리 중...", "OAuth callback page must show the processing label.");
assertIncludes("border-2 border-github-purple-light border-t-transparent rounded-full animate-spin", "OAuth callback page must use the compact spinner.");
assertIncludes("로그인 실패", "OAuth callback page must show the compact failure title.");
assertIncludes("토큰을 받지 못했습니다.", "OAuth callback page must show the missing token copy.");
assertIncludes("다시 로그인", "OAuth callback page must keep the login retry link.");

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

console.log("OAuth callback parity validation passed.");
