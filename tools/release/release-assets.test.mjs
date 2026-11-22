import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import {
  allAssetNames,
  bundleArguments,
  CHROME_EXTENSION_ASSET,
  PLATFORMS,
  packageExtension,
  stageAssets,
  SUMS_FILE,
  writeChecksums,
} from "./release-assets.mjs";

const repoRoot = path.resolve(import.meta.dirname, "../..");

test("release assets list defines all macOS, Windows, Linux, and Chrome Extension packages", () => {
  assert.deepEqual(allAssetNames(), [
    "AquilaBuddy-chrome-extension.zip",
    "AquilaBuddy-linux-arm64.deb",
    "AquilaBuddy-linux-x64.AppImage",
    "AquilaBuddy-linux-x64.deb",
    "AquilaBuddy-linux-x64.rpm",
    "AquilaBuddy-macos-universal.dmg",
    "AquilaBuddy-windows-x64-setup.exe",
  ]);
  assert.equal(bundleArguments("macos-universal"), "app,dmg");
  assert.equal(bundleArguments("windows-x64"), "nsis");
  assert.equal(bundleArguments("linux-x64"), "appimage,deb,rpm");
  assert.equal(bundleArguments("linux-arm64"), "deb");
});

test("stageAssets normalizes Tauri bundle filenames and writeChecksums fails closed on missing assets", () => {
  const workDir = fs.mkdtempSync(path.join(os.tmpdir(), "aquila-buddy-release-test-"));
  try {
    const macDir = path.join(workDir, PLATFORMS["macos-universal"].bundleRoot, "dmg");
    fs.mkdirSync(macDir, { recursive: true });
    fs.writeFileSync(path.join(macDir, "Aquila Buddy_0.1.0_universal.dmg"), "dmg-bytes");

    const stagedDir = path.join(workDir, "staged");
    const staged = stageAssets("macos-universal", workDir, stagedDir);
    assert.deepEqual(staged, ["AquilaBuddy-macos-universal.dmg"]);
    assert.ok(fs.existsSync(path.join(stagedDir, "AquilaBuddy-macos-universal.dmg")));

    assert.throws(() => writeChecksums(stagedDir), /missing:/);

    for (const name of allAssetNames()) {
      const target = path.join(stagedDir, name);
      if (!fs.existsSync(target)) {
        fs.writeFileSync(target, `fixture-${name}`);
      }
    }

    const lines = writeChecksums(stagedDir);
    assert.equal(lines.length, 7);
    const sumsText = fs.readFileSync(path.join(stagedDir, SUMS_FILE), "utf8");
    assert.match(sumsText, /^[0-9a-f]{64}  AquilaBuddy-macos-universal\.dmg$/m);
    assert.match(sumsText, /^[0-9a-f]{64}  AquilaBuddy-chrome-extension\.zip$/m);
  } finally {
    fs.rmSync(workDir, { recursive: true, force: true });
  }
});

test("packageExtension packages current extension runtime files into AquilaBuddy-chrome-extension.zip", () => {
  const outDir = fs.mkdtempSync(path.join(os.tmpdir(), "aquila-buddy-ext-zip-"));
  try {
    const zipPath = packageExtension(repoRoot, outDir);
    assert.equal(path.basename(zipPath), CHROME_EXTENSION_ASSET);
    assert.ok(fs.statSync(zipPath).size > 4096);
  } finally {
    fs.rmSync(outDir, { recursive: true, force: true });
  }
});
