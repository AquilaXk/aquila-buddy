import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import zlib from "node:zlib";
import {
  allAssetNames,
  bundleArguments,
  CHROME_EXTENSION_ASSET,
  CHROME_EXTENSION_FILES,
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
    const zipBytes = fs.readFileSync(zipPath);
    assert.ok(zipBytes.length > 4096);

    const extracted = new Map();
    let offset = 0;
    while (offset + 30 <= zipBytes.length && zipBytes.readUInt32LE(offset) === 0x04034b50) {
      const method = zipBytes.readUInt16LE(offset + 8);
      const compSize = zipBytes.readUInt32LE(offset + 18);
      const nameLen = zipBytes.readUInt16LE(offset + 26);
      const extraLen = zipBytes.readUInt16LE(offset + 28);
      const nameStart = offset + 30;
      const dataStart = nameStart + nameLen + extraLen;
      const name = zipBytes.subarray(nameStart, nameStart + nameLen).toString("utf8");
      const rawPayload = zipBytes.subarray(dataStart, dataStart + compSize);
      const contents = method === 8 ? zlib.inflateRawSync(rawPayload) : rawPayload;
      extracted.set(name, contents);
      offset = dataStart + compSize;
    }

    assert.equal(zipBytes.readUInt32LE(offset), 0x02014b50);
    assert.deepEqual([...extracted.keys()].sort(), [...CHROME_EXTENSION_FILES].sort());
    for (const file of CHROME_EXTENSION_FILES) {
      const expected = fs.readFileSync(path.join(repoRoot, "extension", file));
      assert.deepEqual(extracted.get(file), expected, `round-trip mismatch for ${file}`);
    }
  } finally {
    fs.rmSync(outDir, { recursive: true, force: true });
  }
});
