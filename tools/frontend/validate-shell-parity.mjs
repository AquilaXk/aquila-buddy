import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve } from "node:path";

const repoRoot = resolve(import.meta.dirname, "../..");
const protectedLayoutPath = join(repoRoot, "front/src/components/ProtectedLayout.tsx");
const navbarPath = join(repoRoot, "front/src/components/Navbar.tsx");
const appRoot = join(repoRoot, "front/src/app");

const failures = [];

function readText(path) {
  return readFileSync(path, "utf8");
}

function fail(message) {
  failures.push(message);
}

function assertIncludes(text, needle, message) {
  if (!text.includes(needle)) {
    fail(message);
  }
}

function assertNotIncludes(text, needle, message) {
  if (text.includes(needle)) {
    fail(message);
  }
}

function collectTsxFiles(directory) {
  return readdirSync(directory)
    .flatMap((entry) => {
      const path = join(directory, entry);
      if (statSync(path).isDirectory()) {
        return collectTsxFiles(path);
      }
      return path.endsWith(".tsx") ? [path] : [];
    });
}

const protectedLayout = readText(protectedLayoutPath);
assertIncludes(protectedLayout, "import Navbar from \"./Navbar\";", "ProtectedLayout must use the shell Navbar default export.");
assertIncludes(protectedLayout, "<Navbar />", "ProtectedLayout must render Navbar once.");
assertIncludes(protectedLayout, "<main className=\"mx-auto max-w-6xl px-4 py-8\">", "ProtectedLayout main wrapper changed.");
assertNotIncludes(protectedLayout, "title:", "ProtectedLayout must not accept a title prop.");
assertNotIncludes(protectedLayout, "WorkspaceStatus", "ProtectedLayout must not render workspace status.");
assertNotIncludes(protectedLayout, "Workspace", "ProtectedLayout must not add a workspace heading.");

const navbar = readText(navbarPath);
assertIncludes(navbar, "const navLinks = [", "Navbar must keep the target navLinks contract.");
assertIncludes(navbar, "export default function Navbar()", "Navbar must be the default export.");
assertIncludes(navbar, "<nav className=\"sticky top-0 z-50 border-b border-github-border bg-github-surface\">", "Navbar root shell class changed.");
assertNotIncludes(navbar, "<header", "Navbar must not add an extra header wrapper.");

for (const label of ["대시보드", "추천 이슈", "PR 초안", "내 프로필"]) {
  assertIncludes(navbar, `label: "${label}"`, `Navbar is missing ${label}.`);
}

for (const path of collectTsxFiles(appRoot)) {
  const text = readText(path);
  if (text.includes("<ProtectedLayout title=")) {
    fail(`${relative(repoRoot, path)} still passes a title prop to ProtectedLayout.`);
  }
}

for (const removedPath of [
  "front/src/components/WorkspaceStatus.tsx",
  "front/src/components/workspaceHealthStatus.ts"
]) {
  if (existsSync(join(repoRoot, removedPath))) {
    fail(`${removedPath} should stay removed from the frontend shell.`);
  }
}

if (failures.length > 0) {
  console.error("Frontend shell parity validation failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend shell parity validation passed.");
