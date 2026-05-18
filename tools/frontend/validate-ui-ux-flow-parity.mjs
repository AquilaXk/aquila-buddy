import { spawnSync } from "node:child_process";
import { resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");

const validators = [
  ["shell", "tools/frontend/validate-shell-parity.mjs"],
  ["login", "tools/frontend/validate-login-parity.mjs"],
  ["oauth-callback", "tools/frontend/validate-oauth-callback-parity.mjs"],
  ["dashboard", "tools/frontend/validate-dashboard-parity.mjs"],
  ["profile", "tools/frontend/validate-profile-parity.mjs"],
  ["issues", "tools/frontend/validate-issues-parity.mjs"],
  ["issue-detail", "tools/frontend/validate-issue-detail-parity.mjs"],
  ["pr-draft", "tools/frontend/validate-pr-draft-parity.mjs"],
  ["pr-detail", "tools/frontend/validate-pr-detail-parity.mjs"],
];

const failures = [];

for (const [name, script] of validators) {
  const result = spawnSync(process.execPath, [script], {
    cwd: repoRoot,
    encoding: "utf8",
  });

  if (result.stdout) {
    process.stdout.write(result.stdout);
  }
  if (result.stderr) {
    process.stderr.write(result.stderr);
  }

  if (result.status !== 0) {
    failures.push(name);
  }
}

if (failures.length > 0) {
  console.error(`Frontend UI UX parity validation failed: ${failures.join(", ")}`);
  process.exit(1);
}

console.log("Frontend UI UX parity validation passed.");
