"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { logout } from "@/lib/api";
import { clearAuthState } from "@/lib/auth";

const navLinks = [
  { href: "/dashboard", label: "대시보드" },
  { href: "/issues", label: "추천 이슈" },
  { href: "/pr-draft", label: "PR 초안", matchPrefixes: ["/pr-draft", "/pr/"] },
  { href: "/profile", label: "내 프로필" }
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const mobileMenuId = useId();
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  useEffect(() => {
    setIsMenuOpen(false);
  }, [pathname]);

  async function handleLogout() {
    try {
      await logout();
    } finally {
      clearAuthState();
      router.replace("/login");
    }
  }

  function isActive(link: (typeof navLinks)[number]) {
    if (link.matchPrefixes) {
      return link.matchPrefixes.some((prefix) => pathname.startsWith(prefix));
    }
    return pathname.startsWith(link.href);
  }

  return (
    <nav
      aria-label="Primary"
      className="sticky top-0 z-50 border-b border-github-border bg-github-surface/95 shadow-[0_14px_30px_rgba(0,0,0,0.32)]"
    >
      <div className="mx-auto max-w-6xl px-4">
        <div className="flex min-h-16 items-center justify-between gap-3">
          <Link
            href="/dashboard"
            className="focus-ring flex shrink-0 items-center gap-3 text-lg font-bold text-github-text transition-colors hover:text-github-purple-light"
          >
            <span className="grid h-9 w-9 place-items-center rounded-md border border-github-accent/70 bg-github-bg shadow-inner shadow-black/40">
              <img alt="" className="h-7 w-7 object-contain" height="28" src="/brand-mascot-bank.png" width="28" />
            </span>
            <span>aquila buddy</span>
          </Link>

          <button
            aria-controls={mobileMenuId}
            aria-expanded={isMenuOpen}
            aria-label={isMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
            className="focus-ring inline-grid h-10 w-10 place-items-center rounded-md border border-github-border text-github-muted transition-colors hover:border-github-accent hover:bg-github-bg hover:text-github-text sm:hidden"
            onClick={() => setIsMenuOpen((open) => !open)}
            type="button"
          >
            <span className="flex h-4 w-5 flex-col justify-between" aria-hidden="true">
              <span className={`h-0.5 rounded-full bg-current transition-transform ${isMenuOpen ? "translate-y-[7px] rotate-45" : ""}`} />
              <span className={`h-0.5 rounded-full bg-current transition-opacity ${isMenuOpen ? "opacity-0" : ""}`} />
              <span className={`h-0.5 rounded-full bg-current transition-transform ${isMenuOpen ? "-translate-y-[7px] -rotate-45" : ""}`} />
            </span>
          </button>

          <div className="hidden items-center gap-1 sm:flex">
            <NavItems isActive={isActive} />
            <LogoutButton onLogout={handleLogout} />
          </div>
        </div>

        <div
          className={`${isMenuOpen ? "grid" : "hidden"} gap-2 border-t border-github-border py-3 sm:hidden`}
          id={mobileMenuId}
        >
          <NavItems isActive={isActive} mobile />
          <LogoutButton mobile onLogout={handleLogout} />
        </div>
      </div>
    </nav>
  );
}

function NavItems({
  isActive,
  mobile = false
}: {
  isActive: (link: (typeof navLinks)[number]) => boolean;
  mobile?: boolean;
}) {
  return (
    <>
      {navLinks.map((link) => (
        <Link
          key={link.href}
          className={`focus-ring rounded-md border text-sm font-semibold transition-colors ${
            mobile ? "px-3 py-2.5" : "px-3 py-1.5"
          } ${
            isActive(link)
              ? "border-github-accent bg-github-accent text-github-bg shadow-inner shadow-black/25"
              : "border-transparent text-github-muted hover:border-github-border hover:bg-github-bg hover:text-github-text"
          }`}
          href={link.href}
        >
          {link.label}
        </Link>
      ))}
    </>
  );
}

function LogoutButton({ onLogout, mobile = false }: { onLogout: () => void; mobile?: boolean }) {
  return (
    <button
      className={`rounded-md border border-transparent text-sm font-semibold text-github-muted transition-colors hover:border-red-400/30 hover:bg-red-500/10 hover:text-red-400 ${
        mobile ? "px-3 py-2.5 text-left" : "px-3 py-1.5 sm:ml-3"
      }`}
      onClick={onLogout}
      type="button"
    >
      로그아웃
    </button>
  );
}
