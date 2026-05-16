const tokenKey = "aquila-buddy-token";

export function getOAuthUrl(state?: string) {
  const oauthUrl = process.env.NEXT_PUBLIC_BACKEND_OAUTH_URL ?? "http://localhost:8080/oauth2/authorization/github";

  if (!state) {
    return oauthUrl;
  }

  const separator = oauthUrl.includes("?") ? "&" : "?";
  return `${oauthUrl}${separator}state=${encodeURIComponent(state)}`;
}

export function getToken() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(tokenKey);
}

export function isLoggedIn() {
  return getToken() !== null;
}

export function setToken(token: string) {
  window.localStorage.setItem(tokenKey, token);
}

export function clearToken() {
  window.localStorage.removeItem(tokenKey);
}
