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
        clearAuthState: () => {
          globalThis.__authStateCleared = true;
        },
        getToken: () => globalThis.window?.localStorage?.getItem("aquila-buddy-token") ?? null,
        markLoggedIn: () => {
          globalThis.__authStateMarked = true;
        }
      };
    }

    return require(request);
  };
  module._compile(compiled, apiPath);

  return module.exports;
}

function installBrowserStorage(token) {
  delete globalThis.__authStateCleared;
  delete globalThis.__authStateMarked;
  globalThis.window = {
    localStorage: {
      getItem: (key) => (key === "aquila-buddy-token" ? token : null)
    }
  };
}

function installDocumentCookie(cookie) {
  globalThis.document = {
    cookie
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
    status: 200,
    ok: true,
    json: async () => ({
      success: true,
      status: "success",
      data,
      message: null
    })
  };
}

function unauthorizedResponse() {
  return {
    status: 401,
    ok: false,
    json: async () => ({
      success: false,
      status: "fail",
      data: null,
      message: "로그인이 필요합니다."
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
  assert.equal(requests[0].init.credentials, "include");
  assert.equal(readHeader(requests[0].init.headers, "Authorization"), "Bearer fixture-token");
});

test("getUserProfile includes cookie credentials when no token is stored", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage(null);
  const requests = [];
  globalThis.fetch = async (url, init = {}) => {
    requests.push({ url, init });
    return successResponse(profileFixture);
  };

  const api = loadApiModule();
  await api.getUserProfile();

  assert.equal(requests[0].url, "https://api.example.test/api/v1/users/me");
  assert.equal(requests[0].init.credentials, "include");
  assert.equal(readHeader(requests[0].init.headers, "Authorization"), undefined);
});

test("updateUserProfile keeps JSON content type while adding the Bearer authorization header", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage("fixture-token");
  installDocumentCookie("aquila_buddy_csrf=csrf-token");
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
  assert.equal(requests[0].init.credentials, "include");
  assert.equal(readHeader(requests[0].init.headers, "Authorization"), "Bearer fixture-token");
  assert.equal(readHeader(requests[0].init.headers, "Content-Type"), "application/json");
});

test("mutating requests bootstrap a missing CSRF cookie before sending the API request", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage(null);
  installDocumentCookie("");
  const requests = [];
  globalThis.fetch = async (url, init = {}) => {
    requests.push({ url, init });

    if (String(url).endsWith("/api/v1/auth/refresh")) {
      installDocumentCookie("aquila_buddy_csrf=issued-csrf");
      return successResponse(null);
    }

    if (String(url).endsWith("/api/v1/users/me")) {
      return successResponse(profileFixture);
    }

    throw new Error(`Unexpected request: ${url}`);
  };

  const api = loadApiModule();
  await api.updateUserProfile({
    name: "Aquila",
    email: "aquila@example.com",
    primaryLanguages: [],
    topics: []
  });

  assert.equal(requests.length, 2);
  assert.equal(requests[0].url, "https://api.example.test/api/v1/auth/refresh");
  assert.equal(requests[1].url, "https://api.example.test/api/v1/users/me");
  assert.equal(readHeader(requests[1].init.headers, "X-Aquila-CSRF-Token"), "issued-csrf");
});

test("mutating requests bootstrap a missing CSRF cookie even when a stored Bearer token exists", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage("stale-token");
  installDocumentCookie("");
  const requests = [];
  globalThis.fetch = async (url, init = {}) => {
    requests.push({ url, init });

    if (String(url).endsWith("/api/v1/auth/refresh")) {
      installDocumentCookie("aquila_buddy_csrf=issued-csrf");
      return successResponse(null);
    }

    if (String(url).endsWith("/api/v1/users/me")) {
      return successResponse(profileFixture);
    }

    throw new Error(`Unexpected request: ${url}`);
  };

  const api = loadApiModule();
  await api.updateUserProfile({
    name: "Aquila",
    email: "aquila@example.com",
    primaryLanguages: [],
    topics: []
  });

  assert.equal(requests.length, 2);
  assert.equal(requests[0].url, "https://api.example.test/api/v1/auth/refresh");
  assert.equal(requests[1].url, "https://api.example.test/api/v1/users/me");
  assert.equal(readHeader(requests[1].init.headers, "Authorization"), "Bearer stale-token");
  assert.equal(readHeader(requests[1].init.headers, "X-Aquila-CSRF-Token"), "issued-csrf");
});

test("parallel crawl requests share a single refresh after simultaneous unauthorized responses", async () => {
  process.env.NEXT_PUBLIC_API_URL = "https://api.example.test";
  installBrowserStorage(null);
  installDocumentCookie("aquila_buddy_csrf=csrf-token");
  let refreshRequests = 0;
  const crawlAttemptsByUrl = new Map();
  const requests = [];
  globalThis.fetch = async (url, init = {}) => {
    requests.push({ url, init });

    if (String(url).endsWith("/api/v1/auth/refresh")) {
      refreshRequests += 1;
      return refreshRequests === 1 ? successResponse(null) : unauthorizedResponse();
    }

    if (String(url).includes("/api/v1/issues/crawl/search?")) {
      const attempts = crawlAttemptsByUrl.get(url) ?? 0;
      crawlAttemptsByUrl.set(url, attempts + 1);
      return attempts === 0 ? unauthorizedResponse() : successResponse([]);
    }

    throw new Error(`Unexpected request: ${url}`);
  };

  const api = loadApiModule();
  await Promise.all([
    api.crawlSearchIssues('language:html label:"good first issue" state:open'),
    api.crawlSearchIssues('language:java label:"good first issue" state:open'),
    api.crawlSearchIssues('language:typescript label:"good first issue" state:open')
  ]);

  const crawlRequests = requests.filter(({ url }) => String(url).includes("/api/v1/issues/crawl/search?"));
  assert.equal(refreshRequests, 1);
  assert.equal(crawlRequests.length, 6);
  assert.equal(globalThis.__authStateMarked, true);
  assert.equal(globalThis.__authStateCleared, undefined);
  assert.equal(readHeader(crawlRequests[0].init.headers, "X-Aquila-CSRF-Token"), "csrf-token");
});
