"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ApiError, getUserProfile } from "@/lib/api";
import { authStateChangedEvent, clearAuthState, hydrateDesktopAuthFromVault, isLoggedIn, markLoggedIn } from "@/lib/auth";
import CommandPalette from "./CommandPalette";
import Navbar, { SidebarProvider, SiteHeader } from "./Navbar";
import ShortcutHelpDialog from "./ShortcutHelpDialog";

type ProtectedLayoutProps = {
  children: React.ReactNode;
};

function buildNextTarget(pathname: string): string {
  const query = typeof window !== "undefined" ? window.location.search : "";
  return `${pathname}${query}`;
}

export default function ProtectedLayout({ children }: ProtectedLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [sessionStatus, setSessionStatus] = useState<"checking" | "authenticated" | "unauthenticated" | "error">("checking");
  const [sessionError, setSessionError] = useState<string | null>(null);
  const [retryToken, setRetryToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const nextTarget = buildNextTarget(pathname);

    async function verifySession() {
      setSessionStatus("checking");
      setSessionError(null);

      await hydrateDesktopAuthFromVault();
      if (cancelled) {
        return;
      }

      if (!isLoggedIn()) {
        setSessionStatus("unauthenticated");
        router.replace(`/login?next=${encodeURIComponent(nextTarget)}`);
        return;
      }

      try {
        await getUserProfile();
        if (cancelled) {
          return;
        }
        markLoggedIn();
        setSessionStatus("authenticated");
      } catch (caught) {
        if (cancelled) {
          return;
        }

        const isAuthFailure = (caught instanceof ApiError && caught.isAuthError) || !isLoggedIn();
        if (isAuthFailure) {
          // 401 인증 만료·거부 시 가짜 인증 상태로 통과시키지 않고 Fail-Closed로 세션을 정리한다.
          clearAuthState();
          setSessionStatus("unauthenticated");
          router.replace(`/login?next=${encodeURIComponent(nextTarget)}`);
          return;
        }

        // 일시적 서버/네트워크 오류 시 보호 콘텐츠는 숨긴 채 재시도 경로만 제공한다.
        setSessionError(caught instanceof Error ? caught.message : "요청 처리 중 오류가 발생했습니다.");
        setSessionStatus("error");
      }
    }

    verifySession();

    return () => {
      cancelled = true;
    };
  }, [pathname, retryToken, router]);

  useEffect(() => {
    function handleAuthStateChanged() {
      if (!isLoggedIn()) {
        setSessionStatus("unauthenticated");
        router.replace(`/login?next=${encodeURIComponent(buildNextTarget(pathname))}`);
      }
    }

    window.addEventListener(authStateChangedEvent, handleAuthStateChanged);

    return () => {
      window.removeEventListener(authStateChangedEvent, handleAuthStateChanged);
    };
  }, [pathname, router]);

  if (sessionStatus === "error") {
    return (
      <SidebarProvider>
        <div className="min-h-screen bg-[#f6f8fa] text-[#1f2328] lg:flex">
          <Navbar />
          <CommandPalette />
          <ShortcutHelpDialog />
          <div className="flex min-w-0 flex-1 flex-col">
            <div className="flex min-h-screen flex-1 flex-col bg-[#ffffff] lg:m-2 lg:ml-0 lg:min-h-[calc(100vh-1rem)] lg:rounded-xl lg:border lg:border-[#d1d9e0] lg:shadow-xs">
              <SiteHeader />
              <main className="mx-auto w-full max-w-[1280px] flex-1 px-5 py-6 sm:px-7">
                <section className="primer-box p-6">
                  <div className="rounded-md border border-[#ff8182]/40 bg-[#ffebe9] p-4 text-[13.5px] font-medium text-[#d1242f]">
                    프로필을 불러오지 못했습니다: {sessionError}
                  </div>
                  <div className="mt-4 flex flex-wrap items-center gap-2">
                    <button
                      className="btn-primary focus-ring px-4 py-2 text-xs"
                      onClick={() => setRetryToken((count) => count + 1)}
                      type="button"
                    >
                      다시 시도
                    </button>
                    <button
                      className="btn-secondary focus-ring px-4 py-2 text-xs"
                      onClick={() => {
                        clearAuthState();
                        router.replace(`/login?next=${encodeURIComponent(buildNextTarget(pathname))}`);
                      }}
                      type="button"
                    >
                      다시 로그인
                    </button>
                  </div>
                </section>
              </main>
            </div>
          </div>
        </div>
      </SidebarProvider>
    );
  }

  if (sessionStatus !== "authenticated") {
    return null;
  }

  return (
    <SidebarProvider>
      <div className="min-h-screen bg-[#f6f8fa] text-[#1f2328] lg:flex">
        <Navbar />
        <CommandPalette />
        <ShortcutHelpDialog />
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex min-h-screen flex-1 flex-col bg-[#ffffff] lg:m-2 lg:ml-0 lg:min-h-[calc(100vh-1rem)] lg:rounded-xl lg:border lg:border-[#d1d9e0] lg:shadow-xs">
            <SiteHeader />
            <main className="mx-auto w-full max-w-[1280px] flex-1 px-5 py-6 sm:px-7">
              {children}
            </main>
          </div>
        </div>
      </div>
    </SidebarProvider>
  );
}
