#!/usr/bin/env node
/**
 * Release asset naming, deterministic staging, Chrome extension packaging, and SHA-256 checksum
 * generation for Aquila Buddy. Asset names omit the version number so `releases/latest/download/<name>`
 * links in README.md remain stable across versions.
 *
 * Usage:
 *   node tools/release/release-assets.mjs bundles <platform>
 *   node tools/release/release-assets.mjs stage <platform> <out-dir>
 *   node tools/release/release-assets.mjs package-extension <out-dir>
 *   node tools/release/release-assets.mjs checksums <dir>
 */

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";
import zlib from "node:zlib";

export const SUMS_FILE = "SHA256SUMS.txt";
export const DOWNLOAD_BASE_URL = "https://github.com/AquilaXk/aquila-buddy/releases/latest/download";
export const CHROME_EXTENSION_ASSET = "AquilaBuddy-chrome-extension.zip";
export const CHROME_EXTENSION_FILES = [
  "manifest.json",
  "background.js",
  "content.js",
  "content.css",
  "popup.html",
  "popup.js",
];

const UNIVERSAL_MAC_BUNDLE_ROOT = "front/src-tauri/target/universal-apple-darwin/release/bundle";
const NATIVE_BUNDLE_ROOT = "front/src-tauri/target/release/bundle";

export const PLATFORMS = {
  "macos-universal": {
    tauriBundles: ["app", "dmg"],
    bundleRoot: UNIVERSAL_MAC_BUNDLE_ROOT,
    assets: [
      { dir: "dmg", extension: ".dmg", name: "AquilaBuddy-macos-universal.dmg" },
    ],
  },
  "windows-x64": {
    tauriBundles: ["nsis"],
    bundleRoot: NATIVE_BUNDLE_ROOT,
    assets: [
      { dir: "nsis", extension: "-setup.exe", name: "AquilaBuddy-windows-x64-setup.exe" },
    ],
  },
  "linux-x64": {
    tauriBundles: ["appimage", "deb", "rpm"],
    bundleRoot: NATIVE_BUNDLE_ROOT,
    assets: [
      { dir: "appimage", extension: ".AppImage", name: "AquilaBuddy-linux-x64.AppImage" },
      { dir: "deb", extension: ".deb", name: "AquilaBuddy-linux-x64.deb" },
      { dir: "rpm", extension: ".rpm", name: "AquilaBuddy-linux-x64.rpm" },
    ],
  },
  "linux-arm64": {
    tauriBundles: ["deb"],
    bundleRoot: NATIVE_BUNDLE_ROOT,
    assets: [
      { dir: "deb", extension: ".deb", name: "AquilaBuddy-linux-arm64.deb" },
    ],
  },
};

function compareNames(a, b) {
  if (a < b) return -1;
  return a > b ? 1 : 0;
}

function platformOf(id) {
  const platform = PLATFORMS[id];
  if (!platform) {
    throw new Error(`unknown platform '${id}' (expected one of ${Object.keys(PLATFORMS).join(", ")})`);
  }
  return platform;
}

export function bundleArguments(id) {
  return platformOf(id).tauriBundles.join(",");
}

export function allAssetNames() {
  return [
    ...Object.values(PLATFORMS).flatMap((platform) => platform.assets.map((asset) => asset.name)),
    CHROME_EXTENSION_ASSET,
  ].sort(compareNames);
}

function findBundle(root, platform, asset) {
  const dir = path.join(root, platform.bundleRoot, asset.dir);
  const found = fs.existsSync(dir)
    ? fs
        .readdirSync(dir, { withFileTypes: true })
        .filter((entry) => entry.isFile() && entry.name.endsWith(asset.extension))
        .map((entry) => entry.name)
    : [];

  if (found.length !== 1) {
    throw new Error(
      `expected exactly one ${asset.extension} in ${dir}, found ${found.length}${found.length ? ` (${found.join(", ")})` : ""}`,
    );
  }

  return path.join(dir, found[0]);
}

export function locateBundles(id, root) {
  const platform = platformOf(id);
  return platform.assets.map((asset) => ({
    asset,
    file: findBundle(root, platform, asset),
  }));
}

export function stageAssets(id, root, outDir) {
  const bundles = locateBundles(id, root);
  fs.mkdirSync(outDir, { recursive: true });
  const staged = [];
  for (const { asset, file } of bundles) {
    const targetPath = path.join(outDir, asset.name);
    fs.copyFileSync(file, targetPath);
    staged.push(asset.name);
  }
  return staged;
}

const CRC32_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i += 1) {
    let c = i;
    for (let k = 0; k < 8; k += 1) {
      c = (c & 1) !== 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(buffer) {
  let crc = 0xffffffff;
  for (let i = 0; i < buffer.length; i += 1) {
    crc = CRC32_TABLE[(crc ^ buffer[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function buildDeterministicZip(entries) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;

  for (const { name, data } of entries) {
    const nameBytes = Buffer.from(name, "utf8");
    const checksum = crc32(data);
    const deflated = zlib.deflateRawSync(data, { level: 9 });
    const useDeflate = deflated.length < data.length;
    const payload = useDeflate ? deflated : data;
    const method = useDeflate ? 8 : 0;

    const localHeader = Buffer.alloc(30 + nameBytes.length);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4);
    localHeader.writeUInt16LE(0x0800, 6);
    localHeader.writeUInt16LE(method, 8);
    localHeader.writeUInt16LE(0, 10);
    localHeader.writeUInt16LE(0x0021, 12);
    localHeader.writeUInt32LE(checksum, 14);
    localHeader.writeUInt32LE(payload.length, 18);
    localHeader.writeUInt32LE(data.length, 22);
    localHeader.writeUInt16LE(nameBytes.length, 26);
    localHeader.writeUInt16LE(0, 28);
    nameBytes.copy(localHeader, 30);

    const centralHeader = Buffer.alloc(46 + nameBytes.length);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE((3 << 8) | 20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt16LE(0x0800, 8);
    centralHeader.writeUInt16LE(method, 10);
    centralHeader.writeUInt16LE(0, 12);
    centralHeader.writeUInt16LE(0x0021, 14);
    centralHeader.writeUInt32LE(checksum, 16);
    centralHeader.writeUInt32LE(payload.length, 20);
    centralHeader.writeUInt32LE(data.length, 24);
    centralHeader.writeUInt16LE(nameBytes.length, 28);
    centralHeader.writeUInt16LE(0, 30);
    centralHeader.writeUInt16LE(0, 32);
    centralHeader.writeUInt16LE(0, 34);
    centralHeader.writeUInt16LE(0, 36);
    centralHeader.writeUInt32LE((0o100644 << 16) >>> 0, 38);
    centralHeader.writeUInt32LE(offset, 42);
    nameBytes.copy(centralHeader, 46);

    localParts.push(localHeader, payload);
    centralParts.push(centralHeader);
    offset += localHeader.length + payload.length;
  }

  const centralDir = Buffer.concat(centralParts);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(centralDir.length, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);

  return Buffer.concat([...localParts, centralDir, eocd]);
}

export function packageExtension(root, outDir) {
  const extensionDir = path.join(root, "extension");
  const entries = [...CHROME_EXTENSION_FILES].sort(compareNames).map((file) => {
    const full = path.join(extensionDir, file);
    if (!fs.existsSync(full)) {
      throw new Error(`missing Chrome extension runtime file: ${full}`);
    }
    return { name: file, data: fs.readFileSync(full) };
  });

  fs.mkdirSync(outDir, { recursive: true });
  const outZip = path.resolve(outDir, CHROME_EXTENSION_ASSET);
  fs.writeFileSync(outZip, buildDeterministicZip(entries));
  return outZip;
}

export function writeChecksums(dir) {
  const expected = allAssetNames();
  const present = fs.existsSync(dir)
    ? fs
        .readdirSync(dir, { withFileTypes: true })
        .filter((entry) => entry.isFile() && entry.name !== SUMS_FILE)
        .map((entry) => entry.name)
        .sort(compareNames)
    : [];

  const missing = expected.filter((name) => !present.includes(name));
  const unexpected = present.filter((name) => !expected.includes(name));

  if (missing.length > 0 || unexpected.length > 0) {
    const parts = [];
    if (missing.length > 0) parts.push(`missing: ${missing.join(", ")}`);
    if (unexpected.length > 0) parts.push(`unexpected: ${unexpected.join(", ")}`);
    throw new Error(`release assets in ${dir} are not the expected set (${parts.join("; ")})`);
  }

  const lines = expected.map((name) => {
    const digest = crypto
      .createHash("sha256")
      .update(fs.readFileSync(path.join(dir, name)))
      .digest("hex");
    return `${digest}  ${name}`;
  });

  fs.writeFileSync(path.join(dir, SUMS_FILE), `${lines.join("\n")}\n`, "utf8");
  return lines;
}

function main(argv) {
  const [command, first, second] = argv;
  if (command === "bundles" && first) {
    console.log(bundleArguments(first));
    return;
  }
  if (command === "stage" && first && second) {
    for (const name of stageAssets(first, process.cwd(), second)) {
      console.log(`staged ${name}`);
    }
    return;
  }
  if (command === "package-extension" && first) {
    const outZip = packageExtension(process.cwd(), first);
    console.log(`packaged ${path.basename(outZip)}`);
    return;
  }
  if (command === "checksums" && first) {
    for (const line of writeChecksums(first)) {
      console.log(line);
    }
    return;
  }
  console.error(
    "usage: release-assets.mjs bundles <platform> | stage <platform> <out-dir> | package-extension <out-dir> | checksums <dir>",
  );
  process.exit(2);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main(process.argv.slice(2));
  } catch (err) {
    console.error(`::error::${err.message}`);
    process.exit(1);
  }
}
