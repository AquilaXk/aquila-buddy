#!/usr/bin/env node
/**
 * Deterministic validator for README.md media captures, sections, and multi-OS download links.
 */

import fs from "node:fs";
import path from "node:path";
import { allAssetNames, DOWNLOAD_BASE_URL, SUMS_FILE } from "../release/release-assets.mjs";

const REPO_ROOT = path.resolve(import.meta.dirname, "../..");
const README_PATH = path.join(REPO_ROOT, "README.md");
const MIN_PNG_BYTES = 20 * 1024;
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

const REQUIRED_IMAGES = [
  "front/public/images/aquila-buddy-service-preview.png",
  "front/public/readme/dashboard-overview.png",
  "front/public/readme/issue-recommendations.png",
  "front/public/readme/issue-code-analysis.png",
  "front/public/readme/pr-draft-diff.png",
  "front/public/readme/github-extension-dock.png",
];

const REQUIRED_HEADINGS = [
  "# Aquila Buddy",
  "## 할 수 있는 일",
  "## 주요 화면",
  "## 다운로드",
  "## 설치 유의사항",
  "### macOS",
  "### Windows",
  "### Linux",
  "### Chrome 확장 프로그램",
  "## 개발하기",
  "## 문제 신고",
  "## Credits",
];

function validatePngFile(relPath, failures) {
  const fullPath = path.join(REPO_ROOT, relPath);
  if (!fs.existsSync(fullPath)) {
    failures.push(`Missing README screenshot file: ${relPath}`);
    return;
  }
  const buf = fs.readFileSync(fullPath);
  if (buf.length < MIN_PNG_BYTES) {
    failures.push(`README screenshot too small (${buf.length} bytes < ${MIN_PNG_BYTES}): ${relPath}`);
  }
  if (buf.length < 8 || !buf.subarray(0, 8).equals(PNG_SIGNATURE)) {
    failures.push(`Invalid PNG signature in ${relPath}`);
  }
}

function main() {
  const failures = [];
  if (!fs.existsSync(README_PATH)) {
    console.error("Missing README.md");
    process.exit(1);
  }
  const readme = fs.readFileSync(README_PATH, "utf8");

  for (const heading of REQUIRED_HEADINGS) {
    if (!readme.includes(heading)) {
      failures.push(`README.md is missing required heading: ${heading}`);
    }
  }

  for (const relImg of REQUIRED_IMAGES) {
    if (!readme.includes(relImg)) {
      failures.push(`README.md must embed screenshot: ${relImg}`);
    }
    validatePngFile(relImg, failures);
  }

  for (const asset of [...allAssetNames(), SUMS_FILE]) {
    const url = `${DOWNLOAD_BASE_URL}/${asset}`;
    if (!readme.includes(url)) {
      failures.push(`README.md must link latest download asset: ${url}`);
    }
  }

  for (const docRef of ["tools/smoke/AI_RUNTIME_SMOKE.md", "CONTRIBUTING.md", "SECURITY.md", "THIRD_PARTY_NOTICES.md"]) {
    if (!readme.includes(docRef)) {
      failures.push(`README.md must link repository contract doc: ${docRef}`);
    }
  }

  if (failures.length > 0) {
    console.error("README validation failed:");
    for (const msg of failures) {
      console.error(`- ${msg}`);
    }
    process.exit(1);
  }

  console.log(`README validation passed (${REQUIRED_IMAGES.length} Retina PNG captures, ${allAssetNames().length + 1} download links verified).`);
}

main();
