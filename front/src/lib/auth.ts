const tokenKey = "aquila-buddy-token";
const authStateKey = "aquila-buddy-authenticated";

function getBrowserStorage() {
  if (typeof window === "undefined" || typeof window.localStorage === "undefined") {
    return null;
  }

  return window.localStorage;
}

export function getOAuthUrl(state?: string) {
  const oauthUrl = process.env.NEXT_PUBLIC_BACKEND_OAUTH_URL ?? "http://localhost:8080/oauth2/authorization/github";

  if (!state) {
    return oauthUrl;
  }

  const separator = oauthUrl.includes("?") ? "&" : "?";
  return `${oauthUrl}${separator}state=${encodeURIComponent(state)}`;
}

export function getToken() {
  return getBrowserStorage()?.getItem(tokenKey) ?? null;
}

export function isLoggedIn() {
  const storage = getBrowserStorage();

  return storage?.getItem(authStateKey) === "true" || storage?.getItem(tokenKey) !== null;
}

export function markLoggedIn() {
  const storage = getBrowserStorage();

  storage?.setItem(authStateKey, "true");
  storage?.removeItem(tokenKey);
}

export function clearAuthState() {
  const storage = getBrowserStorage();

  storage?.removeItem(authStateKey);
  storage?.removeItem(tokenKey);
}

export function clearToken() {
  clearAuthState();
}
