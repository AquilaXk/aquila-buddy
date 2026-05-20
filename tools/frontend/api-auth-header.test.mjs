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
const apiPath = resolve(repoRoot, "front/src/lib/api.ts");

function loadApiModule() {
  const source = readFileSync(apiPath, "utf8");
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022
    },
    fileName: apiPath
  }).outputText;

  const module = new Module(apiPath);
  module.filename = apiPath;
  module.paths = Module._nodeModulePaths(dirname(apiPath));
  module.require = (request) => {
    if (request === "./auth") {
      return {
        getToken: () => globalThis.window?.localStorage?.getItem("aquila-buddy-token") ?? null
      };
    }

    return require(request);
  };
  module._compile(compiled, apiPath);

  return module.exports;
}

function installBrowserStorage(token) {
  globalThis.window = {
    localStorage: {
      getItem: (key) => (key === "aquila-buddy-token" ? token : null)
    }
  };
}

function readHeader(headers, name) {
  if (!headers) {
    return undefined;
  }

  if (typeof Headers !== "undefined" && headers instanceof Headers) {
    return headers.get(name) ?? undefined;
  }

  if (Array.isArray(headers)) {
    const found = headers.find(([key]) => key.toLowerCase() === name.toLowerCase());
    return found?.[1];
  }

  const entry = Object.entries(headers).find(([key]) => key.toLowerCase() === name.toLowerCase());
  return entry?.[1];
}

function successResponse(data) {
  return {
    ok: true,
    json: async () => ({
      success: true,
      status: "success",
      data,
      message: null
    })
  };
}

const profileFixture = {
  id: "user-1",
  githubId: 1234,
  login: "aquila",
  name: "Aquila",
  email: "aquila@example.com",
  primaryLanguages: [],
  topics: [],
  profileReady: true,
  vectorUpdatedAt: "2026-05-20T00:00:00Z"
};

test("getUserProfile sends the stored token as a Bearer authorization header", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage("fixture-token");
  const requests = [];
  globalThis.fetch = async (url, init = {}) => {
    requests.push({ url, init });
    return successResponse(profileFixture);
  };

  const api = loadApiModule();
  await api.getUserProfile();

  assert.equal(requests[0].url, "https://api.example.test/api/v1/users/me");
  assert.equal(readHeader(requests[0].init.headers, "Authorization"), "Bearer fixture-token");
});

test("updateUserProfile keeps JSON content type while adding the Bearer authorization header", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage("fixture-token");
  const requests = [];
  globalThis.fetch = async (url, init = {}) => {
    requests.push({ url, init });
    return successResponse(profileFixture);
  };

  const api = loadApiModule();
  await api.updateUserProfile({
    name: "Aquila",
    email: "aquila@example.com",
    primaryLanguages: [],
    topics: []
  });

  assert.equal(requests[0].url, "https://api.example.test/api/v1/users/me");
  assert.equal(readHeader(requests[0].init.headers, "Authorization"), "Bearer fixture-token");
  assert.equal(readHeader(requests[0].init.headers, "Content-Type"), "application/json");
});
