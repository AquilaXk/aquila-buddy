import { existsSync, readFileSync } from "node:fs";
import { join, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const tailwindConfig = readFileSync(join(repoRoot, "front/tailwind.config.ts"), "utf8");
const globalsCss = readFileSync(join(repoRoot, "front/src/app/globals.css"), "utf8");
const layoutPath = join(repoRoot, "front/src/app/layout.tsx");
const manifestPath = join(repoRoot, "front/src/app/manifest.ts");
const navbarPath = join(repoRoot, "front/src/components/Navbar.tsx");
const protectedLayoutPath = join(repoRoot, "front/src/components/ProtectedLayout.tsx");
const layoutSource = readFileSync(layoutPath, "utf8");
const manifestSource = existsSync(manifestPath) ? readFileSync(manifestPath, "utf8") : "";
const navbarSource = readFileSync(navbarPath, "utf8");
const protectedLayoutSource = readFileSync(protectedLayoutPath, "utf8");
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

for (const asset of [
  "front/public/favicon.ico",
  "front/public/apple-touch-icon.png",
  "front/public/brand-mascot.png",
  "front/public/brand-mascot-bank.png",
  "front/public/brand-mascot-blog.png",
  "front/public/blog-favicon.ico",
  "front/public/blog-apple-touch-icon.png",
  "front/public/images/default-profile.svg"
]) {
  if (!existsSync(join(repoRoot, asset))) {
    fail(`Missing brand asset: ${asset}`);
  }
}

assertIncludes(layoutSource, 'applicationName: "aquila buddy"', "Metadata must expose the aquila buddy application name.");
assertIncludes(layoutSource, 'siteName: "aquila buddy"', "OpenGraph metadata must expose the aquila buddy site name.");
assertIncludes(layoutSource, 'url: "/brand-mascot.png"', "OpenGraph metadata must use the representative brand image.");

assertIncludes(manifestSource, 'name: "aquila buddy"', "Manifest must expose the full aquila buddy name.");
assertIncludes(manifestSource, 'short_name: "aquila buddy"', "Manifest must expose the short aquila buddy name.");
assertIncludes(manifestSource, 'src: "/brand-mascot.png"', "Manifest must include the representative brand image.");

assertIncludes(navbarSource, 'aria-label="Primary"', "Navbar must expose a primary navigation label.");
assertIncludes(navbarSource, 'src="/brand-mascot-bank.png"', "Navbar must use the framed brand mascot asset.");
assertIncludes(navbarSource, "border-github-accent", "Navbar must use the brass accent border treatment.");
assertIncludes(navbarSource, "bg-github-accent text-github-bg", "Active nav item must use brass-on-page colors.");
assertIncludes(navbarSource, "h-16", "Navbar must use the taller gothic header rhythm.");
if (navbarSource.includes("tracking-tight")) {
  fail("Navbar must avoid negative letter spacing.");
}

assertIncludes(protectedLayoutSource, "bg-transparent", "Protected shell must not hide the gothic page backdrop.");
assertIncludes(protectedLayoutSource, "max-w-6xl", "Protected shell must keep the existing content width.");
assertIncludes(protectedLayoutSource, "<Navbar />", "Protected shell must keep the existing navigation structure.");

if (failures.length > 0) {
  console.error("Brand gothic parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Brand gothic parity validation passed.");
