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

for (const file of files) {
  const result = spawnSync(process.execPath, ["--check", resolve(repoRoot, file)], {
    encoding: "utf8",
  });

  if (result.status !== 0) {
    process.stderr.write(result.stderr || result.stdout);
    throw new Error(`JavaScript syntax check failed: ${file}`);
  }
}

console.log("extension JavaScript syntax validation passed");
