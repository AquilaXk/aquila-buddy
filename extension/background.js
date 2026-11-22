const ENV_PRESETS = Object.freeze({
  local: Object.freeze({
    key: "local",
    label: "로컬 (localhost)",
    apiBaseUrl: "http://localhost:8080",
    webBaseUrl: "http://localhost:3000",
  }),
  prod: Object.freeze({
    key: "prod",
    label: "운영 (buddy.aquilaxk.site)",
    apiBaseUrl: "https://buddy.aquilaxk.site",
    webBaseUrl: "https://buddy.aquilaxk.site",
  }),
});

const ALLOWED_API_BASES = new Set([ENV_PRESETS.local.apiBaseUrl, ENV_PRESETS.prod.apiBaseUrl]);
const ALLOWED_WEB_BASES = new Set([ENV_PRESETS.local.webBaseUrl, ENV_PRESETS.prod.webBaseUrl]);
const PENDING_OAUTH_KEY = "pendingOAuth";
const MAX_PROCESSED_EXT_CODES = 32;
const BASE64URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
const SHA256_K = [
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
];
let fallbackPendingOAuth = null;
const processedExtCodes = new Set();
let oauthEventQueue = Promise.resolve();

function enqueueOAuthEvent(fn) {
  const run = oauthEventQueue.then(fn, fn);
  oauthEventQueue = run.catch(() => {});
  return run;
}

function rememberProcessedExtCode(extCode) {
  if (processedExtCodes.has(extCode)) {
    return false;
  }
  if (processedExtCodes.size >= MAX_PROCESSED_EXT_CODES) {
    const oldest = processedExtCodes.values().next().value;
    if (oldest !== undefined) {
      processedExtCodes.delete(oldest);
    }
  }
  processedExtCodes.add(extCode);
  return true;
}

function encodeBase64Url(bytes) {
  let out = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i];
    const hasB1 = i + 1 < bytes.length;
    const hasB2 = i + 2 < bytes.length;
    const b1 = hasB1 ? bytes[i + 1] : 0;
    const b2 = hasB2 ? bytes[i + 2] : 0;
    const triplet = (b0 << 16) | (b1 << 8) | b2;
    out += BASE64URL_ALPHABET[(triplet >>> 18) & 63];
    out += BASE64URL_ALPHABET[(triplet >>> 12) & 63];
    if (hasB1) out += BASE64URL_ALPHABET[(triplet >>> 6) & 63];
    if (hasB2) out += BASE64URL_ALPHABET[triplet & 63];
  }
  return out;
}

function toUtf8Bytes(text) {
  if (typeof TextEncoder !== "undefined") {
    return new TextEncoder().encode(text);
  }
  const bytes = [];
  for (let i = 0; i < text.length; i += 1) {
    let code = text.charCodeAt(i);
    if (code < 0x80) {
      bytes.push(code);
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
    } else {
      bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
    }
  }
  return new Uint8Array(bytes);
}

// Minimal pure-JS FIPS 180-4 SHA-256 fallback for non-secure or minimal test VMs lacking crypto.subtle.
function sha256FallbackBytes(inputBytes) {
  const bitLen = inputBytes.length * 8;
  const paddedLen = Math.ceil((inputBytes.length + 9) / 64) * 64;
  const padded = new Uint8Array(paddedLen);
  padded.set(inputBytes);
  padded[inputBytes.length] = 0x80;
  const hiBits = Math.floor(bitLen / 0x100000000);
  const loBits = bitLen >>> 0;
  padded[paddedLen - 8] = (hiBits >>> 24) & 0xff;
  padded[paddedLen - 7] = (hiBits >>> 16) & 0xff;
  padded[paddedLen - 6] = (hiBits >>> 8) & 0xff;
  padded[paddedLen - 5] = hiBits & 0xff;
  padded[paddedLen - 4] = (loBits >>> 24) & 0xff;
  padded[paddedLen - 3] = (loBits >>> 16) & 0xff;
  padded[paddedLen - 2] = (loBits >>> 8) & 0xff;
  padded[paddedLen - 1] = loBits & 0xff;

  const H = [
    0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
    0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
  ];
  const w = new Array(64);
  const rotr = (x, n) => (x >>> n) | (x << (32 - n));

  for (let offset = 0; offset < paddedLen; offset += 64) {
    for (let i = 0; i < 16; i += 1) {
      const idx = offset + i * 4;
      w[i] =
        (padded[idx] << 24) |
        (padded[idx + 1] << 16) |
        (padded[idx + 2] << 8) |
        padded[idx + 3];
    }
    for (let i = 16; i < 64; i += 1) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) | 0;
    }

    let [a, b, c, d, e, f, g, h] = H;
    for (let i = 0; i < 64; i += 1) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + SHA256_K[i] + w[i]) | 0;
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) | 0;
      h = g;
      g = f;
      f = e;
      e = (d + temp1) | 0;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) | 0;
    }

    H[0] = (H[0] + a) | 0;
    H[1] = (H[1] + b) | 0;
    H[2] = (H[2] + c) | 0;
    H[3] = (H[3] + d) | 0;
    H[4] = (H[4] + e) | 0;
    H[5] = (H[5] + f) | 0;
    H[6] = (H[6] + g) | 0;
    H[7] = (H[7] + h) | 0;
  }

  const digest = new Uint8Array(32);
  for (let i = 0; i < 8; i += 1) {
    digest[i * 4] = (H[i] >>> 24) & 0xff;
    digest[i * 4 + 1] = (H[i] >>> 16) & 0xff;
    digest[i * 4 + 2] = (H[i] >>> 8) & 0xff;
    digest[i * 4 + 3] = H[i] & 0xff;
  }
  return digest;
}

function generateCodeVerifier() {
  if (typeof crypto?.getRandomValues !== "function") {
    throw new Error("CSPRNG_UNAVAILABLE");
  }
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return encodeBase64Url(bytes);
}

async function computePkceChallenge(verifier) {
  const bytes = toUtf8Bytes(verifier);
  if (typeof crypto !== "undefined" && crypto.subtle && typeof crypto.subtle.digest === "function") {
    const hashBuffer = await crypto.subtle.digest("SHA-256", bytes);
    return encodeBase64Url(new Uint8Array(hashBuffer));
  }
  return encodeBase64Url(sha256FallbackBytes(bytes));
}

function getSessionStorageArea() {
  return chrome.storage?.session ?? null;
}

async function savePendingOAuth(state) {
  fallbackPendingOAuth = state;
  const sessionArea = getSessionStorageArea();
  if (sessionArea) {
    await sessionArea.set({ [PENDING_OAUTH_KEY]: state }).catch(() => {});
  }
}

async function loadPendingOAuth() {
  const sessionArea = getSessionStorageArea();
  if (sessionArea) {
    const stored = await sessionArea.get(PENDING_OAUTH_KEY).catch(() => ({}));
    if (stored?.[PENDING_OAUTH_KEY]) {
      return stored[PENDING_OAUTH_KEY];
    }
  }
  return fallbackPendingOAuth;
}

async function clearPendingOAuth() {
  fallbackPendingOAuth = null;
  const sessionArea = getSessionStorageArea();
  if (sessionArea) {
    await sessionArea.remove(PENDING_OAUTH_KEY).catch(() => {});
  }
}

async function getActiveEnvironment() {
  const { envKey } = await chrome.storage.local.get("envKey");
  const candidate = Object.hasOwn(ENV_PRESETS, envKey) ? ENV_PRESETS[envKey] : ENV_PRESETS.local;
  if (!ALLOWED_API_BASES.has(candidate.apiBaseUrl) || !ALLOWED_WEB_BASES.has(candidate.webBaseUrl)) {
    return ENV_PRESETS.local;
  }
  return candidate;
}

async function setActiveEnvironment(envKey) {
  if (!Object.hasOwn(ENV_PRESETS, envKey)) {
    throw new Error("INVALID_ENVIRONMENT");
  }
  const nextEnv = ENV_PRESETS[envKey];
  if (!ALLOWED_API_BASES.has(nextEnv.apiBaseUrl) || !ALLOWED_WEB_BASES.has(nextEnv.webBaseUrl)) {
    throw new Error("INVALID_ENVIRONMENT");
  }
  await chrome.storage.local.set({ envKey: nextEnv.key });
  await clearPendingOAuth();
  await clearAuthSession();
  await notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: false });
  return nextEnv;
}

async function getToken() {
  const { token } = await chrome.storage.local.get("token");
  return token || null;
}

async function setToken(token) {
  await chrome.storage.local.set({ token });
}

async function clearAuthSession() {
  await chrome.storage.local.remove(["token", "cachedRecommendations"]);
}

function extractOAuthToken(parsedUrl) {
  const hashParams = new URLSearchParams(
    parsedUrl.hash.startsWith("#") ? parsedUrl.hash.slice(1) : parsedUrl.hash
  );
  return (
    parsedUrl.searchParams.get("token") ||
    parsedUrl.searchParams.get("accessToken") ||
    parsedUrl.searchParams.get("access_token") ||
    parsedUrl.searchParams.get("jwt") ||
    hashParams.get("token") ||
    hashParams.get("access_token")
  );
}

async function exchangeExtensionCode(apiBaseUrl, code, codeVerifier) {
  const response = await fetch(`${apiBaseUrl}/api/v1/auth/extension/exchange`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code, codeVerifier }),
  });

  if (!response.ok) {
    throw new Error(`OAUTH_EXCHANGE_HTTP_${response.status}`);
  }

  const payload = await response.json().catch(() => null);
  const token =
    payload?.data?.token ??
    payload?.data?.accessToken ??
    payload?.token ??
    null;

  if (typeof token !== "string" || token.trim().length === 0) {
    throw new Error("OAUTH_EXCHANGE_INVALID_TOKEN");
  }

  return token.trim();
}

async function handleOAuthTabUpdated(tabId, changeInfo, tabInfo) {
  const url = changeInfo?.url || (changeInfo?.status === "complete" ? tabInfo?.url : "") || "";
  if (!url) {
    return;
  }

  const pending = await loadPendingOAuth();
  if (!pending || pending.tabId !== tabId) {
    return;
  }

  if (!ALLOWED_WEB_BASES.has(pending.webBaseUrl)) {
    await clearPendingOAuth();
    return;
  }

  const redirectPrefix = `${pending.webBaseUrl}/oauth/callback`;
  if (!url.startsWith(redirectPrefix)) {
    return;
  }

  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    await clearPendingOAuth();
    return;
  }

  const errorParam = parsed.searchParams.get("error") || parsed.searchParams.get("error_description");
  if (errorParam) {
    await clearPendingOAuth();
    await clearAuthSession();
    await notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: false, error: errorParam });
    return;
  }

  const extCode = parsed.searchParams.get("ext_code");
  if (extCode) {
    if (!rememberProcessedExtCode(extCode)) {
      return;
    }
    await clearPendingOAuth();
    const apiBaseUrl = ALLOWED_API_BASES.has(pending.apiBaseUrl)
      ? pending.apiBaseUrl
      : (await getActiveEnvironment()).apiBaseUrl;

    if (!pending.codeVerifier || !ALLOWED_API_BASES.has(apiBaseUrl)) {
      await clearAuthSession();
      await notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: false, error: "INVALID_PKCE_STATE" });
      return;
    }

    try {
      const exchangedToken = await exchangeExtensionCode(apiBaseUrl, extCode, pending.codeVerifier);
      await setToken(exchangedToken);
      chrome.tabs.remove(tabId).catch(() => {});
      await notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: true });
    } catch (exchangeError) {
      await clearAuthSession();
      await notifyGitHubTabs({
        type: "AUTH_STATE_CHANGED",
        authenticated: false,
        error: exchangeError?.message || "OAUTH_EXCHANGE_FAILED",
      });
    }
    return;
  }

  if (pending.codeVerifier) {
    return;
  }

  const tokenParam = extractOAuthToken(parsed);
  if (!tokenParam) {
    return;
  }

  await clearPendingOAuth();
  await setToken(tokenParam);
  chrome.tabs.remove(tabId).catch(() => {});
  await notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: true });
}

async function handleOAuthTabRemoved(removedTabId) {
  const pending = await loadPendingOAuth();
  if (pending && pending.tabId === removedTabId) {
    await clearPendingOAuth();
  }
}

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tabInfo) => {
  void enqueueOAuthEvent(() => handleOAuthTabUpdated(tabId, changeInfo, tabInfo));
});

chrome.tabs.onRemoved?.addListener((removedTabId) => {
  void enqueueOAuthEvent(() => handleOAuthTabRemoved(removedTabId));
});

async function startLogin() {
  return enqueueOAuthEvent(async () => {
    const env = await getActiveEnvironment();
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = await computePkceChallenge(codeVerifier);
    const authUrl = `${env.apiBaseUrl}/oauth2/authorization/github?client=extension&code_challenge=${encodeURIComponent(codeChallenge)}&code_challenge_method=S256`;
    const tab = await chrome.tabs.create({ url: authUrl });
    await savePendingOAuth({
      tabId: tab.id,
      webBaseUrl: env.webBaseUrl,
      apiBaseUrl: env.apiBaseUrl,
      codeVerifier,
    });
    return { started: true, tabId: tab.id };
  });
}

async function notifyGitHubTabs(message) {
  const tabs = await chrome.tabs.query({ url: "https://github.com/*" });
  tabs.forEach((tab) => chrome.tabs.sendMessage(tab.id, message).catch(() => {}));
}

async function apiFetch(path, options = {}) {
  const token = await getToken();
  if (!token) {
    throw new Error("NOT_AUTHENTICATED");
  }

  const env = await getActiveEnvironment();
  if (!ALLOWED_API_BASES.has(env.apiBaseUrl)) {
    throw new Error("INVALID_ENVIRONMENT");
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });

  if (response.status === 401) {
    await clearAuthSession();
    await notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: false });
    throw new Error("TOKEN_EXPIRED");
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body?.message || `HTTP_${response.status}`);
  }

  return response.json();
}

async function fetchRecommendedIssuesWithCrawl() {
  const profileRes = await apiFetch("/api/v1/users/me");
  const rawLanguages = Array.isArray(profileRes?.data?.primaryLanguages)
    ? profileRes.data.primaryLanguages
    : [];
  const languages = rawLanguages
    .filter((language) => typeof language === "string" && language.trim().length > 0)
    .slice(0, 3);

  if (languages.length > 0) {
    await Promise.all(
      languages.map((language) =>
        apiFetch(`/api/v1/issues/crawl/search?q=${encodeURIComponent(language)}`, {
          method: "POST",
        }).catch((err) => {
          if (err?.message === "TOKEN_EXPIRED" || err?.message === "NOT_AUTHENTICATED") {
            throw err;
          }
          return null;
        })
      )
    );
  }

  const recData = await apiFetch("/api/v1/issues/recommend");
  await chrome.storage.local.set({ cachedRecommendations: recData });
  return recData;
}

function parsePositiveInt(value) {
  const parsed = Number.parseInt(String(value ?? ""), 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

async function lookupIssueId(repoFullName, issueNumber) {
  const validNumber = parsePositiveInt(issueNumber);
  if (!repoFullName || typeof repoFullName !== "string" || !validNumber) {
    throw new Error("INVALID_ISSUE_REFERENCE");
  }

  const data = await apiFetch(
    `/api/v1/issues/lookup?repo=${encodeURIComponent(repoFullName)}&number=${validNumber}`
  );
  const id = data?.data?.id;
  if (!id) {
    throw new Error("ISSUE_NOT_IN_DB");
  }
  return id;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  switch (message?.type) {
    case "GET_ENVIRONMENT":
      getActiveEnvironment()
        .then((environment) => sendResponse({ ok: true, data: environment }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "SET_ENVIRONMENT":
      setActiveEnvironment(message.envKey)
        .then((environment) => sendResponse({ ok: true, data: environment }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "LOGIN":
      startLogin()
        .then((result) => sendResponse({ ok: true, data: result }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "LOGOUT":
      clearAuthSession()
        .then(() => notifyGitHubTabs({ type: "AUTH_STATE_CHANGED", authenticated: false }))
        .then(() => sendResponse({ ok: true }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "GET_AUTH_STATUS":
      getToken().then((token) => sendResponse({ isLoggedIn: !!token }));
      return true;

    case "GET_RECOMMEND_ISSUES":
      fetchRecommendedIssuesWithCrawl()
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "GET_RECOMMEND_HISTORY":
      apiFetch("/api/v1/issues/recommend/history")
        .then((data) => {
          chrome.storage.local.set({ cachedRecommendations: data });
          sendResponse({ ok: true, data });
        })
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "GET_CACHED_RECOMMENDATIONS":
      chrome.storage.local.get("cachedRecommendations").then(({ cachedRecommendations }) => {
        sendResponse({ ok: true, data: cachedRecommendations ?? null });
      });
      return true;

    case "GET_ISSUE_GUIDE":
      lookupIssueId(message.repoFullName, message.issueNumber)
        .then((id) => apiFetch(`/api/v1/issues/${encodeURIComponent(String(id))}/guide`))
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "GET_ISSUE_PSEUDO":
      lookupIssueId(message.repoFullName, message.issueNumber)
        .then((id) => apiFetch(`/api/v1/issues/${encodeURIComponent(String(id))}/pseudo`))
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "CREATE_PR_DRAFT":
      apiFetch("/api/v1/pr", {
        method: "POST",
        body: JSON.stringify(message.payload),
      })
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "TRANSLATE_PR_DRAFT":
      if (!message.draftId) {
        sendResponse({ ok: false, error: "INVALID_DRAFT_ID" });
        return false;
      }
      apiFetch(`/api/v1/pr/${encodeURIComponent(String(message.draftId))}/translate`, { method: "POST" })
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;
  }
});
