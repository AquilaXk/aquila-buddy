import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const loginPagePath = join(repoRoot, "front/src/app/login/page.tsx");
const loginPage = readFileSync(loginPagePath, "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(needle, message) {
  if (!loginPage.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(needle, message) {
  if (loginPage.includes(needle)) {
    fail(message);
  }
}

assertIncludes("normalizeInternalRedirect", "Login page must keep internal next redirect normalization.");
assertIncludes("const oauthUrl = getOAuthUrl(nextPath);", "Login page must preserve OAuth state propagation.");
assertIncludes("max-w-sm", "Login page must use the target compact card width.");
assertIncludes("Oh My OpenSource", "Login page must show the target brand subtitle.");
assertIncludes("text-3xl font-bold text-github-purple-light", "Login page brand heading style changed.");
assertIncludes("text-xl font-semibold text-github-text", "Login card title style changed.");
assertIncludes("GitHub 계정으로 로그인하여 오픈소스 이슈를 탐색하세요", "Login card description changed.");
assertIncludes("bg-github-purple", "Login button must use the target purple action color.");
assertIncludes("<GitHubIcon />", "Login button must include the GitHub icon.");
assertIncludes("GitHub으로 로그인", "Login button label changed.");
assertIncludes("로그인하면 GitHub 프로필 정보를 사용에 동의하는 것으로 간주합니다.", "Consent copy changed.");
assertIncludes("백엔드 서버:", "Backend server hint must be visible.");
assertIncludes("localhost:8080", "Backend server hint must keep the local backend address.");

assertNotIncludes("GitHub 로그인", "Login page must not use the old GitHub-specific heading.");
assertNotIncludes("GitHub로 계속하기", "Login page must not use the old CTA label.");
assertNotIncludes("bg-github-accent px-4 py-3", "Login CTA must not use the old accent button style.");

if (failures.length > 0) {
  console.error("Login page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Login page parity validation passed.");
