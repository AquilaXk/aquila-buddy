"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { getUserProfile } from "@/lib/api";
import { authStateChangedEvent, clearAuthState, isLoggedIn, markLoggedIn } from "@/lib/auth";
import Navbar from "./Navbar";

type ProtectedLayoutProps = {
  children: React.ReactNode;
};

export default function ProtectedLayout({ children }: ProtectedLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [sessionStatus, setSessionStatus] = useState<"checking" | "authenticated" | "unauthenticated">("checking");

  useEffect(() => {
    let cancelled = false;

    async function verifySession() {
      setSessionStatus("checking");

      if (!isLoggedIn()) {
        setSessionStatus("unauthenticated");
        router.replace(`/login?next=${encodeURIComponent(pathname)}`);
        return;
      }

      try {
        await getUserProfile();
        if (cancelled) {
          return;
        }
        markLoggedIn();
        setSessionStatus("authenticated");
      } catch {
        if (cancelled) {
          return;
        }

        if (!isLoggedIn()) {
          setSessionStatus("unauthenticated");
          router.replace(`/login?next=${encodeURIComponent(pathname)}`);
          return;
        }

        setSessionStatus("authenticated");
      }
    }

    verifySession();

    return () => {
      cancelled = true;
    };
  }, [pathname, router]);

  useEffect(() => {
    function handleAuthStateChanged() {
      if (!isLoggedIn()) {
        setSessionStatus("unauthenticated");
        router.replace(`/login?next=${encodeURIComponent(pathname)}`);
      }
    }

    window.addEventListener(authStateChangedEvent, handleAuthStateChanged);

    return () => {
      window.removeEventListener(authStateChangedEvent, handleAuthStateChanged);
    };
  }, [pathname, router]);

  if (sessionStatus !== "authenticated") {
    return null;
  }

  return (
    <div className="min-h-screen bg-transparent text-github-text">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-8">
        {children}
      </main>
    </div>
  );
}
