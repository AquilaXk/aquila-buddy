import assert from "node:assert/strict";
import { createHash, webcrypto } from "node:crypto";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { test } from "node:test";
import vm from "node:vm";

const backgroundSource = readFileSync(
  resolve(import.meta.dirname, "../../../extension/background.js"),
  "utf8"
);

function createMemoryStorage(initial = {}) {
  const store = { ...initial };
  return {
    store,
    async get(keys) {
      if (typeof keys === "string") {
        return { [keys]: store[keys] };
      }
      if (Array.isArray(keys)) {
        const result = {};
        for (const key of keys) {
          result[key] = store[key];
        }
        return result;
      }
      return { ...store };
    },
    async set(entries) {
      Object.assign(store, entries);
    },
    async remove(keys) {
      const list = Array.isArray(keys) ? keys : [keys];
      for (const key of list) {
        delete store[key];
      }
    },
  };
}

function createBackgroundHarness({ initialLocal = {}, initialSession = {}, fetchImpl, cryptoImpl } = {}) {
  const local = createMemoryStorage(initialLocal);
  const session = createMemoryStorage(initialSession);
  const tabMessages = [];
  const removedTabs = [];
  const createdTabs = [];
  const updatedListeners = [];
  const removedListeners = [];
  const runtimeListeners = [];

  const context = {
    URL,
    URLSearchParams,
    Set,
    Object,
    String,
    Number,
    Error,
    crypto: cryptoImpl === undefined ? webcrypto : cryptoImpl,
    fetch: fetchImpl ?? (async () => {
      throw new Error("unexpected fetch");
    }),
    chrome: {
      storage: { local, session },
      tabs: {
        onUpdated: {
          addListener(fn) {
            updatedListeners.push(fn);
          },
        },
        onRemoved: {
          addListener(fn) {
            removedListeners.push(fn);
          },
        },
        async create({ url }) {
          const tab = { id: 700 + createdTabs.length, url };
          createdTabs.push(tab);
          return tab;
        },
        async remove(tabId) {
          removedTabs.push(tabId);
        },
        async query() {
          return [{ id: 10 }, { id: 11 }];
        },
        async sendMessage(tabId, message) {
          tabMessages.push({ tabId, message });
        },
      },
      runtime: {
        onMessage: {
          addListener(fn) {
            runtimeListeners.push(fn);
          },
        },
      },
    },
  };

  vm.createContext(context);
  vm.runInContext(backgroundSource, context);

  function sendRuntimeMessage(message) {
    return new Promise((resolveResponse) => {
      runtimeListeners[0](message, {}, resolveResponse);
    });
  }

  return {
    local,
    session,
    tabMessages,
    removedTabs,
    createdTabs,
    updatedListeners,
    removedListeners,
    sendRuntimeMessage,
  };
}

test("rejects unknown environment keys fail-closed and clears session when switching allowlisted environments", async () => {
  const harness = createBackgroundHarness({
    initialLocal: { token: "tok-1", cachedRecommendations: { data: [{ issueNumber: 1 }] } },
  });

  const invalid = await harness.sendRuntimeMessage({ type: "SET_ENVIRONMENT", envKey: "https://evil.test" });
  assert.equal(invalid.ok, false);
  assert.equal(invalid.error, "INVALID_ENVIRONMENT");
  assert.equal(harness.local.store.token, "tok-1");

  const switched = await harness.sendRuntimeMessage({ type: "SET_ENVIRONMENT", envKey: "prod" });
  assert.equal(switched.ok, true);
  assert.equal(switched.data.apiBaseUrl, "https://buddy.aquilaxk.site");
  assert.equal(harness.local.store.token, undefined);
  assert.equal(harness.local.store.cachedRecommendations, undefined);
  assert.equal(harness.tabMessages.at(-1)?.message?.type, "AUTH_STATE_CHANGED");
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, false);
});

test("restores pendingOAuth from chrome.storage.session across top-level tabs.onUpdated and fails closed on OAuth error", async () => {
  const harness = createBackgroundHarness({
    initialLocal: { token: "stale-tok", cachedRecommendations: { data: [1] } },
    initialSession: {
      pendingOAuth: { tabId: 77, webBaseUrl: "http://localhost:3000" },
    },
  });

  assert.equal(harness.updatedListeners.length, 1);

  await harness.updatedListeners[0](
    77,
    { url: "http://localhost:3000/oauth/callback?error=access_denied" },
    {}
  );
  await new Promise((r) => setTimeout(r, 10));

  assert.equal(harness.session.store.pendingOAuth, undefined);
  assert.equal(harness.local.store.token, undefined);
  assert.equal(harness.local.store.cachedRecommendations, undefined);
  assert.deepEqual(harness.removedTabs, []);
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, false);
  assert.equal(harness.tabMessages.at(-1)?.message?.error, "access_denied");
});

test("stores token from top-level OAuth callback and clears session + cache on 401", async () => {
  const harness = createBackgroundHarness({
    initialLocal: { cachedRecommendations: { data: [{ issueNumber: 42 }] } },
    initialSession: {
      pendingOAuth: { tabId: 88, webBaseUrl: "https://buddy.aquilaxk.site" },
    },
    fetchImpl: async () => ({
      status: 401,
      ok: false,
      async json() {
        return {};
      },
    }),
  });

  await harness.updatedListeners[0](
    88,
    { url: "https://buddy.aquilaxk.site/oauth/callback?token=jwt-live-123" },
    {}
  );
  await new Promise((r) => setTimeout(r, 10));

  assert.equal(harness.local.store.token, "jwt-live-123");
  assert.equal(harness.session.store.pendingOAuth, undefined);
  assert.deepEqual(harness.removedTabs, [88]);
  assert.equal(harness.tabMessages.at(-1)?.message?.type, "AUTH_STATE_CHANGED");
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, true);

  const expiredRes = await harness.sendRuntimeMessage({ type: "GET_RECOMMEND_ISSUES" });
  assert.equal(expiredRes.ok, false);
  assert.equal(expiredRes.error, "TOKEN_EXPIRED");
  assert.equal(harness.local.store.token, undefined);
  assert.equal(harness.local.store.cachedRecommendations, undefined);
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, false);
});

test("launches PKCE OAuth with S256 code_challenge and exchanges ext_code via POST", async () => {
  const fetchCalls = [];
  const harness = createBackgroundHarness({
    fetchImpl: async (url, init = {}) => {
      fetchCalls.push({ url: String(url), method: init.method ?? "GET", body: init.body });
      if (String(url).endsWith("/api/v1/auth/extension/exchange")) {
        return {
          status: 200,
          ok: true,
          async json() {
            return { success: true, status: "success", data: { token: "pkce-jwt-999" } };
          },
        };
      }
      throw new Error(`unexpected fetch: ${url}`);
    },
  });

  const loginRes = await harness.sendRuntimeMessage({ type: "LOGIN" });
  assert.equal(loginRes.ok, true);
  assert.equal(harness.createdTabs.length, 1);

  const authUrl = new URL(harness.createdTabs[0].url);
  assert.equal(authUrl.pathname, "/oauth2/authorization/github");
  assert.equal(authUrl.searchParams.get("client"), "extension");
  assert.equal(authUrl.searchParams.get("code_challenge_method"), "S256");
  const challenge = authUrl.searchParams.get("code_challenge");
  assert.ok(challenge && challenge.length >= 43 && !/[+/=]/.test(challenge), "valid base64url S256 challenge");

  const pending = harness.session.store.pendingOAuth;
  assert.ok(pending?.codeVerifier && pending.codeVerifier.length >= 43);
  const expectedChallenge = createHash("sha256").update(pending.codeVerifier).digest("base64url");
  assert.equal(challenge, expectedChallenge, "pure-JS SHA-256 fallback must match NIST SHA-256");

  await harness.updatedListeners[0](
    harness.createdTabs[0].id,
    { url: "http://localhost:3000/oauth/callback?ext_code=auth-code-123" },
    {}
  );
  await new Promise((r) => setTimeout(r, 20));

  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].url, "http://localhost:8080/api/v1/auth/extension/exchange");
  assert.equal(fetchCalls[0].method, "POST");
  assert.deepEqual(JSON.parse(fetchCalls[0].body), {
    code: "auth-code-123",
    codeVerifier: pending.codeVerifier,
  });
  assert.equal(harness.local.store.token, "pkce-jwt-999");
  assert.equal(harness.session.store.pendingOAuth, undefined);
  assert.deepEqual(harness.removedTabs, [harness.createdTabs[0].id]);
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, true);
});

test("fails closed when PKCE exchange returns HTTP error or missing token and runs crawl before recommend", async () => {
  const harness = createBackgroundHarness({
    initialSession: {
      pendingOAuth: {
        tabId: 91,
        webBaseUrl: "http://localhost:3000",
        apiBaseUrl: "http://localhost:8080",
        codeVerifier: "verifier-abcdef",
      },
    },
    fetchImpl: async (url) => {
      if (String(url).endsWith("/api/v1/auth/extension/exchange")) {
        return {
          status: 200,
          ok: true,
          async json() {
            return { success: true, data: {} };
          },
        };
      }
      throw new Error(`unexpected fetch: ${url}`);
    },
  });

  await harness.updatedListeners[0](
    91,
    { url: "http://localhost:3000/oauth/callback?ext_code=bad-code" },
    {}
  );
  await new Promise((r) => setTimeout(r, 20));

  assert.equal(harness.local.store.token, undefined);
  assert.equal(harness.session.store.pendingOAuth, undefined);
  assert.deepEqual(harness.removedTabs, []);
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, false);

  const crawlCalls = [];
  const recHarness = createBackgroundHarness({
    initialLocal: { token: "valid-token" },
    fetchImpl: async (url, init = {}) => {
      crawlCalls.push({ url: String(url), method: init.method ?? "GET" });
      if (String(url).endsWith("/api/v1/users/me")) {
        return {
          status: 200,
          ok: true,
          async json() {
            return { success: true, data: { primaryLanguages: ["TypeScript", "Java"] } };
          },
        };
      }
      if (String(url).includes("/api/v1/issues/crawl/search")) {
        return {
          status: 200,
          ok: true,
          async json() {
            return { success: true, data: [] };
          },
        };
      }
      if (String(url).endsWith("/api/v1/issues/recommend")) {
        return {
          status: 200,
          ok: true,
          async json() {
            return { success: true, data: [{ issueNumber: 55, repoFullName: "AquilaXk/aquila-buddy" }] };
          },
        };
      }
      throw new Error(`unexpected fetch: ${url}`);
    },
  });

  const recRes = await recHarness.sendRuntimeMessage({ type: "GET_RECOMMEND_ISSUES" });
  assert.equal(recRes.ok, true);
  assert.equal(crawlCalls[0].url, "http://localhost:8080/api/v1/users/me");
  assert.ok(crawlCalls.some((c) => c.method === "POST" && c.url.includes("/api/v1/issues/crawl/search?q=TypeScript")));
  assert.ok(crawlCalls.some((c) => c.method === "POST" && c.url.includes("/api/v1/issues/crawl/search?q=Java")));
  assert.equal(crawlCalls.at(-1).url, "http://localhost:8080/api/v1/issues/recommend");
});

test("serializes concurrent loading/complete tabs.onUpdated events so ext_code exchanges once without clearing valid token", async () => {
  let exchangeCount = 0;
  const harness = createBackgroundHarness({
    initialSession: {
      pendingOAuth: {
        tabId: 99,
        webBaseUrl: "http://localhost:3000",
        apiBaseUrl: "http://localhost:8080",
        codeVerifier: "race-verifier-12345678901234567890123456789012",
      },
    },
    fetchImpl: async (url) => {
      if (String(url).endsWith("/api/v1/auth/extension/exchange")) {
        exchangeCount += 1;
        if (exchangeCount > 1) {
          return {
            status: 401,
            ok: false,
            async json() {
              return { success: false };
            },
          };
        }
        return {
          status: 200,
          ok: true,
          async json() {
            return { success: true, status: "success", data: { token: "pkce-jwt-race" } };
          },
        };
      }
      throw new Error(`unexpected fetch: ${url}`);
    },
  });

  // Fire loading and complete updates synchronously back-to-back without awaiting between them.
  harness.updatedListeners[0](
    99,
    { status: "loading", url: "http://localhost:3000/oauth/callback?ext_code=race-code-1" },
    { url: "http://localhost:3000/oauth/callback?ext_code=race-code-1" }
  );
  harness.updatedListeners[0](
    99,
    { status: "complete" },
    { status: "complete", url: "http://localhost:3000/oauth/callback?ext_code=race-code-1" }
  );
  await new Promise((r) => setTimeout(r, 30));

  assert.equal(exchangeCount, 1);
  assert.equal(harness.local.store.token, "pkce-jwt-race");
  assert.equal(harness.session.store.pendingOAuth, undefined);
  assert.equal(harness.tabMessages.at(-1)?.message?.authenticated, true);
});

test("fails closed with CSPRNG_UNAVAILABLE when crypto.getRandomValues is missing and verifies SHA-256 fallback without subtle", async () => {
  const noCryptoHarness = createBackgroundHarness({ cryptoImpl: null });
  const failLogin = await noCryptoHarness.sendRuntimeMessage({ type: "LOGIN" });
  assert.equal(failLogin.ok, false);
  assert.equal(failLogin.error, "CSPRNG_UNAVAILABLE");

  const noSubtleHarness = createBackgroundHarness({
    cryptoImpl: { getRandomValues: webcrypto.getRandomValues.bind(webcrypto) },
  });
  const okLogin = await noSubtleHarness.sendRuntimeMessage({ type: "LOGIN" });
  assert.equal(okLogin.ok, true);
  const authUrl = new URL(noSubtleHarness.createdTabs[0].url);
  const expectedChallenge = createHash("sha256")
    .update(noSubtleHarness.session.store.pendingOAuth.codeVerifier)
    .digest("base64url");
  assert.equal(authUrl.searchParams.get("code_challenge"), expectedChallenge);
});
