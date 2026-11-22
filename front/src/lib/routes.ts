import { isTauriDesktop } from "./desktop";

function usesDesktopQueryRouting(): boolean {
  return process.env.NEXT_PUBLIC_DESKTOP_EXPORT === "1" || isTauriDesktop();
}

export function issueDetailHref(id: string): string {
  const encoded = encodeURIComponent(id);
  return usesDesktopQueryRouting() ? `/issues/_/?id=${encoded}` : `/issues/${encoded}`;
}

export function prDetailHref(id: string): string {
  const encoded = encodeURIComponent(id);
  return usesDesktopQueryRouting() ? `/pr/_/?id=${encoded}` : `/pr/${encoded}`;
}

export function resolveDynamicRouteId(paramId: string | undefined, queryId: string | null): string {
  if (queryId && (!paramId || paramId === "_")) {
    return queryId;
  }
  return paramId ?? "";
}
