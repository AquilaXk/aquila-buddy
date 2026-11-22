import assert from "node:assert/strict";
import { createRequire } from "node:module";
import Module from "node:module";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const ts = require("../../front/node_modules/typescript");
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const desktopPath = resolve(repoRoot, "front/src/lib/desktop.ts");
const routesPath = resolve(repoRoot, "front/src/lib/routes.ts");

function compileModule(filePath, mocks = {}) {
  const source = readFileSync(filePath, "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: filePath,
  }).outputText;

  const mod = new Module(filePath);
  mod.filename = filePath;
  mod.paths = Module._nodeModulePaths(dirname(filePath));
  mod.require = (request) => {
    if (request in mocks) {
      return mocks[request];
    }
    return require(request);
  };
  mod._compile(compiled, filePath);
  return mod.exports;
}

test("routes helper preserves standard URLs in web mode and switches to placeholder queries in desktop export mode", () => {
  const previousExport = process.env.NEXT_PUBLIC_DESKTOP_EXPORT;
  const previousWindow = globalThis.window;

  try {
    delete globalThis.window;
    process.env.NEXT_PUBLIC_DESKTOP_EXPORT = "";
    const webRoutes = compileModule(routesPath, { "./desktop": { isTauriDesktop: () => false } });
    assert.equal(webRoutes.issueDetailHref("issue 1/2"), "/issues/issue%201%2F2");
    assert.equal(webRoutes.prDetailHref("draft-42"), "/pr/draft-42");

    process.env.NEXT_PUBLIC_DESKTOP_EXPORT = "1";
    const desktopRoutes = compileModule(routesPath, { "./desktop": { isTauriDesktop: () => true } });
    assert.equal(desktopRoutes.issueDetailHref("issue-261"), "/issues/_/?id=issue-261");
    assert.equal(desktopRoutes.prDetailHref("draft-42"), "/pr/_/?id=draft-42");

    assert.equal(desktopRoutes.resolveDynamicRouteId("_", "issue-261"), "issue-261");
    assert.equal(desktopRoutes.resolveDynamicRouteId("issue-99", "ignored"), "issue-99");
    assert.equal(desktopRoutes.resolveDynamicRouteId(undefined, null), "");
  } finally {
    process.env.NEXT_PUBLIC_DESKTOP_EXPORT = previousExport;
    globalThis.window = previousWindow;
  }
});

test("desktop helper constructs PKCE loopback authorization URL without external SDK dependencies", () => {
  const previousWindow = globalThis.window;
  try {
    globalThis.window = {};
    const desktop = compileModule(desktopPath);
    assert.equal(desktop.isTauriDesktop(), false);

    const authUrl = new URL(
      desktop.buildDesktopOAuthAuthorizeUrl(
        "https://buddy.aquilaxk.site/oauth2/authorization/github",
        "challenge_value_123",
        49152
      )
    );
    assert.equal(authUrl.searchParams.get("client"), "desktop");
    assert.equal(authUrl.searchParams.get("code_challenge"), "challenge_value_123");
    assert.equal(authUrl.searchParams.get("code_challenge_method"), "S256");
    assert.equal(authUrl.searchParams.get("loopback_port"), "49152");

    globalThis.window = {
      __TAURI_INTERNALS__: {
        invoke: async () => "ok",
      },
    };
    assert.equal(desktop.isTauriDesktop(), true);
  } finally {
    globalThis.window = previousWindow;
  }
});
