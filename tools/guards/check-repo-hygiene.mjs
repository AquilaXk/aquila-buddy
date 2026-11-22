#!/usr/bin/env node
/**
 * Aquila Buddy Repository Hygiene & Specification Guard
 *
 * Verifies:
 * 1. Presence of required repository, build, notice, guard, and workflow files.
 * 2. Zero local-only agent configuration files, symlinks, or Windows-reserved path segments tracked in Git.
 * 3. Enforce bug-report-only intake templates (.github/ISSUE_TEMPLATE/bug_report.yml & config.yml) and forbid PR templates/label/bot configs.
 * 4. Zero prohibited tool attribution traces or retired brand references in tracked text files.
 * 5. Valid JavaScript syntax across repository scripts and extension sources.
 * 6. Source files in back/front/extension remain below the 1,000-line ceiling.
 */

import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { prohibitedPatternsIn } from "./prohibited-terms.mjs";

const ROOT = process.cwd();
const LINE_CEILING = 1000;
const WINDOWS_RESERVED_SEGMENT = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\.[^.]*)*$/i;

const REQUIRED_TRACKED_CONTRACTS = [
  "README.md",
  "CONTRIBUTING.md",
  "SECURITY.md",
  "THIRD_PARTY_NOTICES.md",
  "TODO.md",
  "compose.yaml",
  "back/build.gradle",
  "front/package.json",
  "extension/manifest.json",
  ".github/ISSUE_TEMPLATE/bug_report.yml",
  ".github/ISSUE_TEMPLATE/config.yml",
  ".github/workflows/ci.yml",
  ".github/workflows/desktop-release.yml",
  "front/src-tauri/Cargo.toml",
  "front/src-tauri/tauri.conf.json",
  "tools/guards/prohibited-terms.mjs",
  "tools/guards/check-commit-messages.mjs",
  "tools/readme/validate-readme.mjs",
  "tools/release/release-assets.mjs",
];

// Local-only filenames/directories/prefixes that must NEVER be tracked by Git (Base64 encoded)
const FORBIDDEN_GIT_PATH_B64 = [
  "QUdFTlRTLm1k",                 // AGENTS.md
  "Q0xBVURFLm1k",                 // CLAUDE.md
  "Q0xBVURFLmxvY2FsLm1k",         // CLAUDE.local.md
  "R0VNSU5JLm1k",                 // GEMINI.md
  "Q1VSU09SLm1k",                 // CURSOR.md
  "Q09QSUxPVC5tZA==",             // COPILOT.md
  "LmFnZW50cy8=",                 // .agents/
  "LmNsYXVkZS8=",                 // .claude/
  "LmNvZGV4Lw==",                 // .codex/
  "LmdlbWluaS8=",                 // .gemini/
  "LmN1cnNvci8=",                 // .cursor/
  "LmFpZGVy",                     // .aider (file or prefix)
  "LndpbmRzdXJmLw==",             // .windsurf/
  "LmFpLw==",                     // .ai/
  "LnBsYXl3cmlnaHQtbWNwLw==",     // .playwright-mcp/
  "ZG9jcy9hZ2VudC8=",             // docs/agent/
  "LmNvZGVyYWJiaXQueWFtbA==",     // .coderabbit.yaml
  "LmNvZGVyYWJiaXQueW1s",         // .coderabbit.yml
];

const FORBIDDEN_GIT_PATHS = FORBIDDEN_GIT_PATH_B64.map((b) =>
  Buffer.from(b, "base64").toString("utf8")
);

const ALLOWED_ISSUE_TEMPLATE_FILES = new Set(["bug_report.yml", "config.yml"]);
const ISSUE_TEMPLATE_DIR = ".github/ISSUE_TEMPLATE/";
const FORBIDDEN_INTAKE_NAME =
  /(^|\/)(pull_request_template(\.[^/]*)?|labeler\.ya?ml|labels\.ya?ml|issue_template\.[^/]*|dependabot\.ya?ml)$/i;
const FORBIDDEN_INTAKE_DIR = /(^|\/)pull_request_template\//i;

export function findIntakeViolations(paths) {
  const problems = [];
  for (const rel of paths) {
    const normalized = rel.replaceAll("\\", "/");
    if (normalized.toLowerCase().startsWith(ISSUE_TEMPLATE_DIR.toLowerCase())) {
      const subPath = normalized.slice(ISSUE_TEMPLATE_DIR.length);
      if (!ALLOWED_ISSUE_TEMPLATE_FILES.has(subPath)) {
        problems.push(
          `${normalized}: only bug_report.yml and config.yml may live in ${ISSUE_TEMPLATE_DIR}`
        );
      }
    } else if (FORBIDDEN_INTAKE_NAME.test(normalized) || FORBIDDEN_INTAKE_DIR.test(normalized)) {
      problems.push(`${normalized}: pull request templates, label configs, and dependabot configs are forbidden`);
    }
  }
  return problems;
}

const IGNORED_EXACT_DIRS = new Set([
  ".git",
  ".gradle",
  ".gradle-user",
  ".next",
  "node_modules",
  "coverage",
  "target",
  ".worktrees",
  ".superpowers",
  ".scratch",
  ...FORBIDDEN_GIT_PATHS.filter((p) => p.endsWith("/") && !p.includes("/")).map((p) =>
    p.slice(0, -1).toLowerCase()
  ),
]);

const IGNORED_PATH_PREFIXES = [
  "back/build/",
  "back/out/",
  "back/bin/",
  "front/build/",
  "front/out/",
  "front/src-tauri/gen/",
  "front/src-tauri/target/",
  "dist/",
  "dist-release/",
  "docs/",
];

const TEXT_EXTENSIONS = new Set([
  ".md",
  ".java",
  ".gradle",
  ".sql",
  ".ts",
  ".tsx",
  ".js",
  ".mjs",
  ".cjs",
  ".css",
  ".html",
  ".yml",
  ".yaml",
  ".json",
  ".rs",
  ".toml",
  ".desktop",
  ".sh",
  ".txt",
  ".properties",
  ".example",
  ".svg",
  ".tf",
]);

export function isForbiddenTrackedPath(trackedPath) {
  const normalized = trackedPath.replaceAll("\\", "/").toLowerCase();
  const segments = normalized.split("/");

  for (const rawForbidden of FORBIDDEN_GIT_PATHS) {
    const fLower = rawForbidden.toLowerCase();
    if (fLower.endsWith("/")) {
      const bare = fLower.slice(0, -1);
      if (bare.includes("/")) {
        if (normalized === bare || normalized.startsWith(fLower)) {
          return true;
        }
      } else if (segments.includes(bare)) {
        return true;
      }
    } else if (fLower === ".aider") {
      if (segments.some((seg) => seg === ".aider" || seg.startsWith(".aider"))) {
        return true;
      }
    } else if (normalized === fLower || segments.includes(fLower)) {
      return true;
    }
  }
  return false;
}

function shouldSkipCandidateRel(rel) {
  const normalized = rel.replaceAll("\\", "/");
  const lower = normalized.toLowerCase();
  if (isForbiddenTrackedPath(normalized)) return true;
  if (IGNORED_PATH_PREFIXES.some((prefix) => lower === prefix.slice(0, -1) || lower.startsWith(prefix))) {
    return true;
  }
  const segments = lower.split("/");
  return segments.some((seg) => IGNORED_EXACT_DIRS.has(seg));
}

function isInspectableTextFile(rel) {
  const ext = path.extname(rel).toLowerCase();
  const base = path.basename(rel);
  return (
    TEXT_EXTENSIONS.has(ext) ||
    base === "LICENSE" ||
    base === "Dockerfile" ||
    base === ".gitignore" ||
    base === ".dockerignore"
  );
}

function listGitTrackedFiles() {
  const raw = execFileSync("git", ["ls-files", "-z"], {
    cwd: ROOT,
    encoding: "utf8",
    maxBuffer: 64 * 1024 * 1024,
    stdio: ["ignore", "pipe", "pipe"],
  });
  return raw
    .split("\0")
    .map((s) => s.trim())
    .filter(Boolean);
}

function listGitCandidateFiles() {
  const raw = execFileSync(
    "git",
    ["ls-files", "-z", "--cached", "--others", "--exclude-standard"],
    {
      cwd: ROOT,
      encoding: "utf8",
      maxBuffer: 64 * 1024 * 1024,
      stdio: ["ignore", "pipe", "pipe"],
    }
  );
  const collected = [];
  for (const rel of new Set(raw.split("\0").filter(Boolean))) {
    if (shouldSkipCandidateRel(rel)) continue;
    const full = path.join(ROOT, rel);
    let stat;
    try {
      stat = fs.statSync(full);
    } catch {
      continue;
    }
    if (!stat.isFile()) continue;
    if (isInspectableTextFile(rel)) {
      collected.push(full);
    }
  }
  return collected;
}

function isLineCeilingScoped(rel) {
  if (rel.startsWith("back/src/main/java/") && rel.endsWith(".java")) {
    return true;
  }
  if (
    rel.startsWith("front/src/") &&
    (rel.endsWith(".ts") || rel.endsWith(".tsx") || rel.endsWith(".css"))
  ) {
    return true;
  }
  if (rel.startsWith("extension/")) {
    const subPath = rel.slice("extension/".length);
    if (
      !subPath.includes("/") &&
      (subPath.endsWith(".js") || subPath.endsWith(".css") || subPath.endsWith(".html"))
    ) {
      return true;
    }
  }
  return false;
}

function countLinesLf(text) {
  let count = 0;
  const len = text.length;
  if (len === 0) return 0;
  for (let i = 0; i < len; i += 1) {
    if (text.charCodeAt(i) === 10) count += 1;
  }
  return text.charCodeAt(len - 1) === 10 ? count : count + 1;
}

function main() {
  const errors = [];

  for (const rel of REQUIRED_TRACKED_CONTRACTS) {
    const full = path.join(ROOT, rel);
    if (!fs.existsSync(full)) {
      errors.push(`Missing required repository file: ${rel}`);
    }
  }

  let trackedList;
  try {
    trackedList = listGitTrackedFiles();
  } catch (err) {
    errors.push(`Failed to list Git tracked files (Fail-Closed): ${err.message}`);
    trackedList = [];
  }

  for (const tracked of trackedList) {
    if (tracked.split("/").some((seg) => WINDOWS_RESERVED_SEGMENT.test(seg))) {
      errors.push(`Path uses a Windows-reserved segment name: ${tracked}`);
    }
    if (isForbiddenTrackedPath(tracked)) {
      errors.push(`Forbidden local-only file or symlink is tracked by Git: ${tracked}`);
    }
  }
  errors.push(...findIntakeViolations(trackedList));

  let files = [];
  try {
    files = listGitCandidateFiles();
  } catch (err) {
    errors.push(`Failed to enumerate candidate files (Fail-Closed): ${err.message}`);
  }

  for (const file of files) {
    const rel = path.relative(ROOT, file).replaceAll("\\", "/");
    if (rel === "tools/guards/check-repo-hygiene.mjs" || rel === "tools/guards/prohibited-terms.mjs") {
      continue;
    }

    const content = fs.readFileSync(file, "utf8");
    for (const pattern of prohibitedPatternsIn(rel, content)) {
      errors.push(
        `Prohibited attribution or retired brand reference detected in ${rel} (pattern length: ${pattern.source.length})`
      );
    }

    if (isLineCeilingScoped(rel)) {
      const lineCount = countLinesLf(content);
      if (lineCount >= LINE_CEILING) {
        errors.push(`Source file ${rel} exceeds ${LINE_CEILING}-line ceiling (${lineCount} lines)`);
      }
    }

    if (rel.endsWith(".js") || rel.endsWith(".mjs") || rel.endsWith(".cjs")) {
      try {
        execFileSync(process.execPath, ["--check", file], {
          cwd: ROOT,
          stdio: "pipe",
        });
      } catch (err) {
        const stderr = err?.stderr ? err.stderr.toString("utf8").trim() : String(err);
        errors.push(`JavaScript syntax check failed in ${rel}: ${stderr}`);
      }
    }
  }

  if (errors.length > 0) {
    console.error("Repository hygiene check failed:");
    for (const err of errors) {
      console.error(`  - ${err}`);
    }
    process.exit(1);
  }

  console.log(
    `Repository hygiene check passed (${files.length} repository files inspected, ${REQUIRED_TRACKED_CONTRACTS.length} required contracts verified, 0 local-only/template files tracked, 1,000-line ceiling verified).`
  );
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main();
}
