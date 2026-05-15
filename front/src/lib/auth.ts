const tokenKey = "aquila-buddy-token";

export function getOAuthUrl() {
  return process.env.NEXT_PUBLIC_BACKEND_OAUTH_URL ?? "http://localhost:8080/oauth2/authorization/github";
}

export function getToken() {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(tokenKey);
}

export function setToken(token: string) {
  window.localStorage.setItem(tokenKey, token);
}

export function clearToken() {
  window.localStorage.removeItem(tokenKey);
}
