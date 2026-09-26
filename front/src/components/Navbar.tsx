"use client";

import { useEffect, useId, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  LayoutDashboard,
  Sparkles,
  GitPullRequest,
  User,
  LogOut,
  Menu,
  X
} from "lucide-react";
import { logout } from "@/lib/api";
import { clearAuthState } from "@/lib/auth";

const navLinks = [
  { href: "/dashboard", label: "대시보드", icon: LayoutDashboard },
  { href: "/issues", label: "추천 이슈", icon: Sparkles },
  { href: "/pr-draft", label: "PR 초안", matchPrefixes: ["/pr-draft", "/pr/"], icon: GitPullRequest },
  { href: "/profile", label: "내 프로필", icon: User }
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
      className="sticky top-0 z-50 border-b border-github-border bg-github-surface/95 backdrop-blur-sm shadow-[0_4px_24px_rgba(0,0,0,0.28)]"
    >
      <div className="mx-auto max-w-6xl px-4">
        <div className="flex min-h-16 items-center justify-between gap-3">
          <Link
            href="/dashboard"
            className="focus-ring flex shrink-0 items-center gap-3 text-lg font-bold text-github-text transition-colors hover:text-brand-400 group"
          >
            <span className="grid h-9 w-9 place-items-center rounded-xl border border-brand-500/40 bg-dark-scaffold shadow-inner shadow-black/40 group-hover:border-brand-500 transition-colors">
              <img alt="" className="h-7 w-7 object-contain" height="28" src="/brand-mascot-bank.png" width="28" />
            </span>
            <span className="tracking-wide">aquila buddy</span>
          </Link>

          <button
            aria-controls={mobileMenuId}
            aria-expanded={isMenuOpen}
            aria-label={isMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
            className="focus-ring inline-grid h-10 w-10 place-items-center rounded-xl border border-github-border text-github-muted transition-colors hover:border-brand-500 hover:bg-dark-elevated hover:text-github-text sm:hidden"
            onClick={() => setIsMenuOpen((open) => !open)}
            type="button"
          >
            {isMenuOpen ? (
              <X className="h-5 w-5" />
            ) : (
              <Menu className="h-5 w-5" />
            )}
          </button>

          <div className="hidden items-center gap-1.5 sm:flex">
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
      {navLinks.map((link) => {
        const Icon = link.icon;
        const active = isActive(link);
        return (
          <Link
            key={link.href}
            className={`focus-ring flex items-center gap-1.5 rounded-xl border text-sm font-semibold transition-all ${
              mobile ? "px-3.5 py-2.5" : "px-3 py-1.5"
            } ${
              active
                ? "border-brand-500 bg-brand-500 text-white shadow-sm shadow-brand-500/25"
                : "border-transparent text-github-muted hover:border-github-border hover:bg-dark-elevated hover:text-github-text"
            }`}
            href={link.href}
          >
            <Icon className="h-4 w-4 shrink-0" />
            <span>{link.label}</span>
          </Link>
        );
      })}
    </>
  );
}

function LogoutButton({ onLogout, mobile = false }: { onLogout: () => void; mobile?: boolean }) {
  return (
    <button
      className={`flex items-center gap-1.5 rounded-xl border border-transparent text-sm font-semibold text-github-muted transition-colors hover:border-red-400/30 hover:bg-red-500/10 hover:text-red-400 ${
        mobile ? "px-3.5 py-2.5 text-left" : "px-3 py-1.5 sm:ml-3"
      }`}
      onClick={onLogout}
      type="button"
    >
      <LogOut className="h-4 w-4 shrink-0" />
      <span>로그아웃</span>
    </button>
  );
}
