"use client";

import { useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { isLoggedIn } from "@/lib/auth";
import { Navbar } from "./Navbar";
import { WorkspaceStatus } from "./WorkspaceStatus";

type ProtectedLayoutProps = {
  title: string;
  children: React.ReactNode;
};

export function ProtectedLayout({ title, children }: ProtectedLayoutProps) {
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
        <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-sm font-semibold text-github-accent">Workspace</p>
            <h1 className="mt-1 text-2xl font-bold text-github-text">{title}</h1>
          </div>
          <WorkspaceStatus />
        </div>
        {children}
      </main>
    </div>
  );
}
