import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..");
const manifestPath = resolve(repoRoot, "extension", "manifest.json");
const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));

assertEqual(manifest.manifest_version, 3, "manifest_version must be 3");
assertEqual(manifest.name, "Aquila Buddy", "manifest.name");
assertText(manifest.version, "version");
assertText(manifest.description, "description");
assertText(manifest.action?.default_popup, "action.default_popup");
assertEqual(manifest.action?.default_title, "Aquila Buddy", "action.default_title");
assertText(manifest.background?.service_worker, "background.service_worker");
assertIncludes(manifest.permissions, "storage", "permissions");
assertIncludes(manifest.permissions, "tabs", "permissions");
assertIncludes(manifest.host_permissions, "https://github.com/*", "host_permissions");
assertIncludes(manifest.host_permissions, "http://localhost:8080/*", "host_permissions");

if (JSON.stringify(manifest).toLowerCase().includes("omos")) {
  fail("manifest must not include old service name (OMOS)");
}

const githubContentScript = manifest.content_scripts?.find((script) =>
  Array.isArray(script.matches) && script.matches.includes("https://github.com/*")
);

if (!githubContentScript) {
  fail("content_scripts must include https://github.com/* match");
}

assertIncludes(githubContentScript.js, "content.js", "github content script js");
assertIncludes(githubContentScript.css, "content.css", "github content script css");

console.log("extension manifest validation passed");

function assertEqual(actual, expected, label) {
  if (actual !== expected) {
    fail(`${label}: expected ${expected}, received ${actual}`);
  }
}

function assertText(value, label) {
  if (typeof value !== "string" || value.trim() === "") {
    fail(`${label} must be a non-empty string`);
  }
}

function assertIncludes(values, expected, label) {
  if (!Array.isArray(values) || !values.includes(expected)) {
    fail(`${label} must include ${expected}`);
  }
}

function fail(message) {
  throw new Error(message);
}
