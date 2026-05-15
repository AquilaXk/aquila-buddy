export type SearchParamsLike = {
  get(name: string): string | null;
};

export type OAuthCallbackParams = {
  token: string | null;
  error: string | null;
  redirectTo: string;
};

const tokenParamNames = ["token", "access_token", "jwt"] as const;
const defaultRedirectPath = "/dashboard";

export function readOAuthCallbackParams(searchParams: SearchParamsLike): OAuthCallbackParams {
  return {
    token: readFirstPresentParam(searchParams, tokenParamNames),
    error: readOAuthError(searchParams),
    redirectTo: normalizeInternalRedirect(searchParams.get("next") ?? searchParams.get("state"))
  };
}

export function normalizeInternalRedirect(value: string | null): string {
  if (!value) {
    return defaultRedirectPath;
  }

  const trimmedValue = value.trim();

  if (!trimmedValue.startsWith("/") || trimmedValue.startsWith("//")) {
    return defaultRedirectPath;
  }

  return trimmedValue;
}

function readOAuthError(searchParams: SearchParamsLike): string | null {
  return searchParams.get("error_description") ?? searchParams.get("error");
}

function readFirstPresentParam(
  searchParams: SearchParamsLike,
  names: readonly string[]
): string | null {
  for (const name of names) {
    const value = searchParams.get(name);

    if (value && value.trim()) {
      return value;
    }
  }

  return null;
}
