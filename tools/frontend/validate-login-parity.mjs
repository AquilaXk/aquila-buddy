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
assertIncludes("lg:grid-cols-2", "Login page must use a 2-column developer split layout on desktop.");
assertIncludes("#0d1117", "Login page left preview panel must use GitHub dark canvas #0d1117.");
assertIncludes("#161b22", "Login page left preview cards must use #161b22 surface.");
assertIncludes("#30363d", "Login page left preview cards must use #30363d border.");
assertIncludes("#1f2328", "Login page right auth panel must use Primer primary ink #1f2328.");
assertIncludes("#d1d9e0", "Login page right auth card must use Primer hairline border #d1d9e0.");
assertIncludes("Aquila Buddy", "Login page must show the service name.");
assertIncludes("오픈소스 기여 워크스페이스", "Login page must show the clean Korean subtitle.");
assertIncludes("로그인", "Login card must show the login title.");
assertIncludes("GitHub 계정으로 로그인하여 오픈소스 이슈를 탐색하세요", "Login card description changed.");
assertIncludes("btn-primary", "Login button must use the primary solid CTA button style.");
assertIncludes("<GitHubIcon />", "Login button must include the GitHub icon.");
assertIncludes("GitHub으로 로그인", "Login button label changed.");
assertIncludes("로그인 시 GitHub 공개 프로필 및 기여 이력 조회에 동의하게 됩니다.", "Consent copy must use natural Korean grammar.");
assertNotIncludes("OAUTH SESSION", "Login page must not display OAUTH SESSION internal banner.");
assertNotIncludes("bg-dark-scaffold", "Login page must not use legacy bg-dark-scaffold class.");
assertNotIncludes("rounded-[14px]", "Login page must not use oversized 14px emblem radius.");
assertNotIncludes("OpenSource contribution assistant", "Login page must not use English slop subtitle.");
assertNotIncludes("로그인하면 GitHub 프로필 정보를 사용에 동의하는 것으로 간주합니다.", "Login page must not use ungrammatical consent copy.");
assertNotIncludes("lavender-glow-sm", "Login page must not use lavender-glow-sm.");
assertNotIncludes("shadow-2xl", "Login page must not use heavy shadow-2xl.");
assertNotIncludes("github-", "Login page must not use duplicate github-* color classes.");
assertNotIncludes("localhost:8080", "Login page must not expose hardcoded local backend address.");
assertNotIncludes("백엔드 서버:", "Login page must not expose obsolete backend server hint.");
assertNotIncludes("GitHub 로그인", "Login page must not use the old GitHub-specific heading.");
assertNotIncludes("GitHub로 계속하기", "Login page must not use the old CTA label.");
assertNotIncludes("OMOS", "Login page must not use the old service name.");
assertNotIncludes("Oh My OpenSource", "Login page must not use the old service subtitle.");

if (failures.length > 0) {
  console.error("Login page parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Login page parity validation passed.");
