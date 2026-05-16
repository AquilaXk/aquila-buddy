const tokenKey = "aquila-buddy-token";

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
  return getToken() !== null;
}

export function setToken(token: string) {
  getBrowserStorage()?.setItem(tokenKey, token);
}

export function clearToken() {
  getBrowserStorage()?.removeItem(tokenKey);
}
