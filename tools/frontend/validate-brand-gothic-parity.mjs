import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const tailwindConfig = readFileSync(join(repoRoot, "front/tailwind.config.ts"), "utf8");
const globalsCss = readFileSync(join(repoRoot, "front/src/app/globals.css"), "utf8");
const failures = [];

function fail(message) {
  failures.push(message);
}

function assertIncludes(source, needle, message) {
  if (!source.includes(needle)) {
    fail(message);
  }
}

assertIncludes(tailwindConfig, "#101214", "Tailwind theme must expose the gothic page color.");
assertIncludes(tailwindConfig, "#171a1d", "Tailwind theme must expose the gothic surface color.");
assertIncludes(tailwindConfig, "#c2a35b", "Tailwind theme must expose the brass accent color.");
assertIncludes(tailwindConfig, "#efe7d3", "Tailwind theme must expose the parchment text color.");

assertIncludes(globalsCss, "--gothic-page", "Global CSS must define gothic page token.");
assertIncludes(globalsCss, "--gothic-brass", "Global CSS must define gothic brass token.");
assertIncludes(globalsCss, "--gothic-parchment", "Global CSS must define gothic parchment token.");
assertIncludes(globalsCss, "repeating-linear-gradient", "Global CSS must include the gothic grid treatment.");

if (failures.length > 0) {
  console.error("Brand gothic parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Brand gothic parity validation passed.");
