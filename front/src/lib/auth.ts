import { isTauriDesktop, vaultDeleteToken, vaultGetToken } from "./desktop";

const tokenKey = "aquila-buddy-token";
const authStateKey = "aquila-buddy-authenticated";
export const authStateChangedEvent = "aquila-buddy-auth-state-changed";
let desktopTokenMemory: string | null = null;

function getBrowserStorage() {
  if (typeof window === "undefined" || typeof window.localStorage === "undefined") {
    return null;
  }

  return window.localStorage;
}

export function getOAuthUrl(nextPath?: string) {
  const oauthUrl = process.env.NEXT_PUBLIC_BACKEND_OAUTH_URL ?? "http://localhost:8080/oauth2/authorization/github";

  if (!nextPath) {
    return oauthUrl;
  }

  const separator = oauthUrl.includes("?") ? "&" : "?";
  return `${oauthUrl}${separator}next=${encodeURIComponent(nextPath)}`;
}

export function getToken() {
  if (desktopTokenMemory !== null) {
    return desktopTokenMemory;
  }
  return getBrowserStorage()?.getItem(tokenKey) ?? null;
}

export function setDesktopAccessToken(token: string | null): void {
  desktopTokenMemory = token;
  if (token) {
    safeSetItem(authStateKey, "true");
    safeRemoveItem(tokenKey);
    notifyAuthStateChanged();
  }
}

export async function hydrateDesktopAuthFromVault(): Promise<boolean> {
  if (!isTauriDesktop()) {
    return false;
  }
  if (desktopTokenMemory !== null) {
    return true;
  }
  try {
    const token = await vaultGetToken();
    if (token && token.trim().length > 0) {
      setDesktopAccessToken(token);
      return true;
    }
  } catch {
    // OS 키체인 접근 실패 시 미인증 상태로 처리한다.
  }
  return false;
}

function safeGetItem(key: string): string | null {
  const storage = getBrowserStorage();
  if (!storage) {
    return null;
  }
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

function safeSetItem(key: string, value: string): void {
  const storage = getBrowserStorage();
  if (!storage) {
    return;
  }
  try {
    storage.setItem(key, value);
  } catch {
    // 프라이빗 모드/스토리지 용량 제한 시 예외로 화면이 중단되지 않게 방어한다.
  }
}

function safeRemoveItem(key: string): void {
  const storage = getBrowserStorage();
  if (!storage) {
    return;
  }
  try {
    storage.removeItem(key);
  } catch {
    // 스토리지 접근 실패 시 조용히 무시한다.
  }
}

export function isLoggedIn() {
  if (desktopTokenMemory !== null) {
    return true;
  }
  const storage = getBrowserStorage();
  if (!storage) {
    return false;
  }

  return safeGetItem(authStateKey) === "true" || safeGetItem(tokenKey) !== null;
}

export function markLoggedIn() {
  const alreadyMarked = safeGetItem(authStateKey) === "true";
  const hadLegacyToken = safeGetItem(tokenKey) !== null;

  safeSetItem(authStateKey, "true");
  safeRemoveItem(tokenKey);

  if (!alreadyMarked || hadLegacyToken) {
    notifyAuthStateChanged();
  }
}

export function clearAuthState() {
  const wasAuthenticated = isLoggedIn();
  desktopTokenMemory = null;
  safeRemoveItem(authStateKey);
  safeRemoveItem(tokenKey);
  void vaultDeleteToken().catch(() => {});
  if (wasAuthenticated) {
    notifyAuthStateChanged();
  }
}

export function clearToken() {
  clearAuthState();
}

function notifyAuthStateChanged() {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new Event(authStateChangedEvent));
}
