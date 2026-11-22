type TauriInternals = {
  invoke: <T = unknown>(command: string, args?: Record<string, unknown>) => Promise<T>;
};

export type DesktopOAuthCallbackPayload = {
  code?: string | null;
  error?: string | null;
};

export type LocalGitStatusEntry = {
  path: string;
  status: string;
};

const AUTH_TOKEN_VAULT_KEY = "auth_token";
const EXCHANGE_TIMEOUT_MS = 15_000;

function getTauriInternals(): TauriInternals | null {
  if (typeof window === "undefined") {
    return null;
  }
  const candidate = (window as unknown as { __TAURI_INTERNALS__?: TauriInternals }).__TAURI_INTERNALS__;
  if (!candidate || typeof candidate.invoke !== "function") {
    return null;
  }
  return candidate;
}

export function isTauriDesktop(): boolean {
  return getTauriInternals() !== null;
}

export async function invokeDesktop<T>(command: string, args?: Record<string, unknown>): Promise<T> {
  const internals = getTauriInternals();
  if (!internals) {
    throw new Error("데스크톱 런타임을 찾을 수 없습니다.");
  }
  return internals.invoke<T>(command, args);
}

export async function openExternalUrl(url: string): Promise<void> {
  await invokeDesktop<void>("open_external_url", { url });
}

export async function vaultGetToken(): Promise<string | null> {
  if (!isTauriDesktop()) {
    return null;
  }
  return invokeDesktop<string | null>("vault_get", { key: AUTH_TOKEN_VAULT_KEY });
}

export async function vaultSetToken(token: string): Promise<void> {
  if (!isTauriDesktop()) {
    return;
  }
  await invokeDesktop<void>("vault_set", { key: AUTH_TOKEN_VAULT_KEY, value: token });
}

export async function vaultDeleteToken(): Promise<void> {
  if (!isTauriDesktop()) {
    return;
  }
  await invokeDesktop<void>("vault_delete", { key: AUTH_TOKEN_VAULT_KEY });
}

export async function getLocalGitStatus(repoDir: string): Promise<LocalGitStatusEntry[]> {
  return invokeDesktop<LocalGitStatusEntry[]>("git_status", { repoDir });
}

export async function getLocalGitDiff(repoDir: string, filePath?: string): Promise<string> {
  return invokeDesktop<string>("git_diff", {
    repoDir,
    filePath: filePath ?? null
  });
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 1) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

async function generatePkcePair(): Promise<{ verifier: string; challenge: string }> {
  if (typeof crypto === "undefined" || !crypto.getRandomValues || !crypto.subtle) {
    throw new Error("PKCE 암호화 모듈을 사용할 수 없습니다.");
  }
  const randomBytes = new Uint8Array(48);
  crypto.getRandomValues(randomBytes);
  const verifier = encodeBase64Url(randomBytes);
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  const challenge = encodeBase64Url(new Uint8Array(digest));
  return { verifier, challenge };
}

export function buildDesktopOAuthAuthorizeUrl(
  baseOAuthUrl: string,
  challenge: string,
  loopbackPort: number
): string {
  const url = new URL(baseOAuthUrl);
  url.searchParams.set("client", "desktop");
  url.searchParams.set("code_challenge", challenge);
  url.searchParams.set("code_challenge_method", "S256");
  url.searchParams.set("loopback_port", String(loopbackPort));
  return url.toString();
}

export async function startDesktopPkceLogin(
  baseOAuthUrl: string,
  apiBaseUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
): Promise<string> {
  const { verifier, challenge } = await generatePkcePair();
  const loopbackPort = await invokeDesktop<number>("oauth_start_loopback");
  const authUrl = buildDesktopOAuthAuthorizeUrl(baseOAuthUrl, challenge, loopbackPort);
  const waitCallback = invokeDesktop<DesktopOAuthCallbackPayload>("oauth_await_loopback");
  waitCallback.catch(() => {});

  await openExternalUrl(authUrl);
  const callback = await waitCallback;
  if (callback.error) {
    throw new Error(`GitHub 인증에 실패했습니다 (${callback.error}).`);
  }
  if (!callback.code) {
    throw new Error("인증 코드를 받지 못했습니다.");
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), EXCHANGE_TIMEOUT_MS);
  try {
    const cleanApi = apiBaseUrl.replace(/\/+$/, "");
    const response = await fetch(`${cleanApi}/api/v1/auth/extension/exchange`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        code: callback.code,
        codeVerifier: verifier
      }),
      signal: controller.signal
    });
    const payload = (await response.json()) as {
      success?: boolean;
      data?: { token?: string };
      message?: string;
    };
    if (!response.ok || !payload.success || !payload.data?.token) {
      throw new Error(payload.message || "데스크톱 로그인 토큰 교환에 실패했습니다.");
    }
    await vaultSetToken(payload.data.token);
    return payload.data.token;
  } finally {
    clearTimeout(timer);
  }
}
