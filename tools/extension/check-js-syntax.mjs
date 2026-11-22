import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..");
const files = [
  "extension/background.js",
  "extension/content.js",
  "extension/popup.js",
];

import { readFileSync } from "node:fs";

for (const file of files) {
  const content = readFileSync(resolve(repoRoot, file), "utf8");
  if (content.toLowerCase().includes("omos")) {
    throw new Error(`${file} must not contain old service name (OMOS)`);
  }

  const result = spawnSync(process.execPath, ["--check", resolve(repoRoot, file)], {
    encoding: "utf8",
  });

  if (result.status !== 0) {
    process.stderr.write(result.stderr || result.stdout);
    throw new Error(`JavaScript syntax check failed: ${file}`);
  }
}

console.log("extension JavaScript syntax validation passed");
