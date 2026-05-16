"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { isLoggedIn } from "@/lib/auth";
import { Navbar } from "./Navbar";

type ProtectedLayoutProps = {
  children: React.ReactNode;
};

export function ProtectedLayout({ children }: ProtectedLayoutProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);

    if (!isLoggedIn()) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [pathname, router]);

  if (!mounted || !isLoggedIn()) {
    return null;
  }

  return (
    <div className="min-h-screen bg-github-bg text-github-text">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-8">
        {children}
      </main>
    </div>
  );
}
