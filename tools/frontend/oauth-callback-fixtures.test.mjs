import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { describe, it } from "node:test";

const repoRoot = resolve(import.meta.dirname, "../..");
const ts = await import(pathToFileURL(join(repoRoot, "front/node_modules/typescript/lib/typescript.js")));

async function importTypeScriptModule(path) {
  const source = readFileSync(path, "utf8");
  const transpiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
  });
  const encodedSource = Buffer.from(transpiled.outputText, "utf8").toString("base64");
  return import(`data:text/javascript;base64,${encodedSource}`);
}

function searchParams(entries) {
  const params = new Map(Object.entries(entries));

  return {
    get(name) {
      return params.get(name) ?? null;
    },
  };
}

const {
  normalizeInternalRedirect,
  readOAuthCallbackParams,
} = await importTypeScriptModule(join(repoRoot, "front/src/lib/oauthCallback.ts"));

describe("OAuth callback fixtures", () => {
  it("stores a normal token and follows the internal state redirect", () => {
    assert.deepEqual(readOAuthCallbackParams(searchParams({ token: "jwt-token", state: "/issues/123" })), {
      token: "jwt-token",
      error: null,
      redirectTo: "/issues/123",
    });
  });

  it("accepts alternate token names and gives next priority over state", () => {
    assert.deepEqual(
      readOAuthCallbackParams(searchParams({ access_token: "access-token", state: "/profile", next: "/dashboard" })),
      {
        token: "access-token",
        error: null,
        redirectTo: "/dashboard",
      }
    );
  });

  it("keeps the missing-token fixture on the safe dashboard redirect", () => {
    assert.deepEqual(readOAuthCallbackParams(searchParams({ state: "/profile" })), {
      token: null,
      error: null,
      redirectTo: "/profile",
    });
  });

  it("keeps the error fixture message from the provider query", () => {
    assert.deepEqual(readOAuthCallbackParams(searchParams({ error_description: "bad code" })), {
      token: null,
      error: "bad code",
      redirectTo: "/dashboard",
    });
  });

  it("normalizes unsafe external redirects to the dashboard", () => {
    assert.equal(normalizeInternalRedirect("https://evil.example/dashboard"), "/dashboard");
    assert.equal(normalizeInternalRedirect("//evil.example/dashboard"), "/dashboard");
    assert.equal(normalizeInternalRedirect("   /profile   "), "/profile");
  });
});
