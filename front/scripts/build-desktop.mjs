#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const frontDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const nextBin = path.join(frontDir, "node_modules", "next", "dist", "bin", "next");
if (!fs.existsSync(nextBin)) {
  console.error(`Missing Next.js CLI binary at ${nextBin}`);
  process.exit(1);
}

const defaultApiUrl = "https://buddy.aquilaxk.site";
const apiUrl = (process.env.NEXT_PUBLIC_API_URL || defaultApiUrl).replace(/\/+$/, "");
const oauthUrl = process.env.NEXT_PUBLIC_BACKEND_OAUTH_URL || `${apiUrl}/oauth2/authorization/github`;

const result = spawnSync(process.execPath, [nextBin, "build", "--webpack"], {
  cwd: frontDir,
  stdio: "inherit",
  env: {
    ...process.env,
    BUILD_TARGET: "desktop",
    NEXT_PUBLIC_DESKTOP_EXPORT: "1",
    NEXT_PUBLIC_API_URL: apiUrl,
    NEXT_PUBLIC_BACKEND_OAUTH_URL: oauthUrl,
    NEXT_TELEMETRY_DISABLED: "1"
  }
});

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}

process.exit(result.status ?? 1);
