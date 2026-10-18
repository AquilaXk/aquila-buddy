import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..");
const popupPath = resolve(repoRoot, "extension", "popup.html");
const html = readFileSync(popupPath, "utf8");
const contentCssPath = resolve(repoRoot, "extension", "content.css");
const contentCss = readFileSync(contentCssPath, "utf8");

assertIncludes(html, "<span>Aquila Buddy</span>", "popup brand mark");
assertNotIncludes(html, "✦", "popup must not include decorative sparkle symbol");
assertNotIncludes(html, "OMOS", "popup brand mark must not include old service name");
assertNotIncludes(html, 'style="', "popup HTML must not include inline style attributes");
assertNotIncludes(html, "#0d1117", "popup must not include dark GitHub background");
assertNotIncludes(html, "#161b22", "popup must not include dark card surface");
assertIncludes(html, "#ffffff", "popup must include pure light-first canvas token");
assertIncludes(html, "#f8f9fc", "popup must include subtle container surface token");
assertIncludes(html, "#121317", "popup must include primary charcoal ink token");
assertIncludes(html, "tabular-nums", "popup must enforce tabular-nums on numeric elements");
assertNotIncludes(html, "text-transform: uppercase", "popup must not use uppercase micro-label cliché");
assertNotIncludes(html, "letter-spacing: 0.06em", "popup must not use wide uppercase tracking");
assertIncludes(html, ".rec-card:last-child", "popup recommendation list must use flush hairline-divided rows");
assertMatches(html, /<div id="rec-list"><\/div>/, "empty recommendation list container");
assertIncludes(html, '<script src="popup.js"></script>', "popup script include");
assertNotIncludes(html, "로그인 후 추천 이슈를 확인할 수 있습니다.", "static empty recommendation copy");

assertIncludes(contentCss, "#e6f4ea", "content.css badge light-first background");
assertIncludes(contentCss, "#137333", "content.css badge light-first ink");
assertIncludes(contentCss, "tabular-nums", "content.css badge tabular-nums");

console.log("extension popup validation passed");

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    fail(`${label} must include ${expected}`);
  }
}

function assertNotIncludes(value, expected, label) {
  if (value.includes(expected)) {
    fail(`${label} must not include ${expected}`);
  }
}

function assertMatches(value, pattern, label) {
  if (!pattern.test(value)) {
    fail(`${label} does not match ${pattern}`);
  }
}

function fail(message) {
  throw new Error(message);
}
