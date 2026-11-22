#!/usr/bin/env node
/**
 * Commit message guard for pushed commits and external pull requests: commit messages in
 * `base..head` (or `--single <rev>`) and `PR_TITLE` (when set) must not carry prohibited tool
 * names, cross-repo references, or attribution trailers.
 *
 * Uses `git log -z` (NUL-delimited records) and validates full SHA headers so control characters
 * inside a commit body can never spoof record boundaries.
 *
 * Usage:
 *   node tools/guards/check-commit-messages.mjs <base-sha-or-ref> <head-sha-or-ref>
 *   node tools/guards/check-commit-messages.mjs --single <sha-or-ref>
 */

import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";
import { PROHIBITED_PATTERNS } from "./prohibited-terms.mjs";

const SAFE_REF = /^[0-9A-Za-z._/~^-]{1,128}$/;
const FULL_SHA = /^[0-9a-f]{40,64}$/i;

export function findProhibited(label, text) {
  return PROHIBITED_PATTERNS.filter((pattern) => pattern.test(text)).map(
    (pattern) => `${label}: prohibited content (${pattern.source.length} chars)`
  );
}

function assertValidRef(ref) {
  if (!SAFE_REF.test(ref) || ref.startsWith("-")) {
    throw new Error(`Invalid git revision: ${ref}`);
  }
}

export function parseNulLogOutput(out) {
  if (!out) return [];
  return out
    .split("\0")
    .filter((record) => record.length > 0)
    .map((record) => {
      const newline = record.indexOf("\n");
      const sha = (newline < 0 ? record : record.slice(0, newline)).trim();
      if (!FULL_SHA.test(sha)) {
        throw new Error("Malformed git log record: missing full commit SHA header");
      }
      const message = newline < 0 ? "" : record.slice(newline + 1);
      return { sha, message };
    });
}

export function commitMessages(base, head, run = execFileSync) {
  assertValidRef(base);
  assertValidRef(head);
  const out = run("git", ["log", "-z", "--format=%H%n%B", `${base}..${head}`], {
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  return parseNulLogOutput(out);
}

export function singleCommitMessage(rev, run = execFileSync) {
  assertValidRef(rev);
  const out = run("git", ["log", "-n", "1", "-z", "--format=%H%n%B", rev], {
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
  const parsed = parseNulLogOutput(out);
  if (parsed.length === 0) {
    throw new Error(`Commit not found: ${rev}`);
  }
  return parsed;
}

export function checkPullRequest({ commits, title = "" }) {
  const problems = commits.flatMap(({ sha, message }) =>
    findProhibited(`commit ${sha.slice(0, 8)}`, message)
  );
  return [...problems, ...findProhibited("PR title", title)];
}

function main() {
  const args = process.argv.slice(2);
  let commits;
  if (args[0] === "--single" && args[1] && args.length === 2) {
    commits = singleCommitMessage(args[1]);
  } else if (args.length === 2 && args[0] && args[1]) {
    commits = commitMessages(args[0], args[1]);
  } else {
    console.error(
      "Usage: node tools/guards/check-commit-messages.mjs (<base> <head> | --single <rev>)"
    );
    process.exit(1);
  }

  const problems = checkPullRequest({
    commits,
    title: process.env.PR_TITLE ?? "",
  });
  if (problems.length > 0) {
    console.error(`Commit message guard failed:\n  - ${problems.join("\n  - ")}`);
    process.exit(1);
  }
  console.log("Commit message guard passed.");
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) main();
