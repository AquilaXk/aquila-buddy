import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, "..", "..");
const workflowPath = resolve(repoRoot, ".github", "workflows", "ci.yml");
const workflow = readFileSync(workflowPath, "utf8");

assertIncludes(workflow, "name: Extension", "Extension CI job");
assertIncludes(workflow, "npm --prefix extension run validate", "Extension static validation step");
assertIncludes(workflow, "npm --prefix extension run test:e2e", "Extension live E2E step");

console.log("extension CI E2E validation passed");

function assertIncludes(value, expected, label) {
  if (!value.includes(expected)) {
    throw new Error(`${label} must include ${expected}`);
  }
}
