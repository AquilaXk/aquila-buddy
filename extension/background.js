const API_BASE = "http://localhost:8080";
const REDIRECT_URI_PREFIX = "http://localhost:3000/oauth/callback";

async function getToken() {
  const { token } = await chrome.storage.local.get("token");
  return token || null;
}

async function setToken(token) {
  await chrome.storage.local.set({ token });
}

async function clearToken() {
  await chrome.storage.local.remove("token");
}

async function startLogin() {
  const tab = await chrome.tabs.create({ url: `${API_BASE}/oauth2/authorization/github` });

  return new Promise((resolve) => {
    function listener(tabId, changeInfo, tabInfo) {
      if (tabId !== tab.id || changeInfo.status !== "complete") {
        return;
      }

      const url = tabInfo.url || "";
      if (!url.startsWith(REDIRECT_URI_PREFIX)) {
        return;
      }

      const tokenParam = new URL(url).searchParams.get("token");
      if (tokenParam) {
        setToken(tokenParam).then(() => {
          chrome.tabs.remove(tabId);
          chrome.tabs.onUpdated.removeListener(listener);
          notifyGitHubTabs({ type: "AUTH_SUCCESS" });
          resolve();
        });
      }
    }

    chrome.tabs.onUpdated.addListener(listener);
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

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
  });

  if (response.status === 401) {
    await clearToken();
    throw new Error("TOKEN_EXPIRED");
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body?.message || `HTTP_${response.status}`);
  }

  return response.json();
}

async function lookupIssueId(repoFullName, issueNumber) {
  const data = await apiFetch(
    `/api/v1/issues/lookup?repo=${encodeURIComponent(repoFullName)}&number=${issueNumber}`
  );
  const id = data?.data?.id;
  if (!id) {
    throw new Error("ISSUE_NOT_IN_DB");
  }
  return id;
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  switch (message.type) {
    case "LOGIN":
      startLogin()
        .then(() => sendResponse({ ok: true }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "LOGOUT":
      clearToken().then(() => sendResponse({ ok: true }));
      return true;

    case "GET_AUTH_STATUS":
      getToken().then((token) => sendResponse({ isLoggedIn: !!token }));
      return true;

    case "GET_RECOMMEND_ISSUES":
      apiFetch("/api/v1/issues/recommend")
        .then((data) => {
          chrome.storage.local.set({ cachedRecommendations: data });
          sendResponse({ ok: true, data });
        })
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
        .then((id) => apiFetch(`/api/v1/issues/${id}/guide`))
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;

    case "GET_ISSUE_PSEUDO":
      lookupIssueId(message.repoFullName, message.issueNumber)
        .then((id) => apiFetch(`/api/v1/issues/${id}/pseudo`))
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
      apiFetch(`/api/v1/pr/${message.draftId}/translate`, { method: "POST" })
        .then((data) => sendResponse({ ok: true, data }))
        .catch((error) => sendResponse({ ok: false, error: error.message }));
      return true;
  }
});
