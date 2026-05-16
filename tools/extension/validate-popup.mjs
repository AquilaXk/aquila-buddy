import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..");
const popupPath = resolve(repoRoot, "extension", "popup.html");
const html = readFileSync(popupPath, "utf8");

assertIncludes(html, "<span>✦ OMOS</span>", "popup brand mark");
assertMatches(html, /<div id="rec-list"><\/div>/, "empty recommendation list container");
assertIncludes(html, '<script src="popup.js"></script>', "popup script include");
assertNotIncludes(html, "로그인 후 추천 이슈를 확인할 수 있습니다.", "static empty recommendation copy");

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
