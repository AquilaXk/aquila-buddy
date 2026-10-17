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
      className="sticky top-0 z-50 border-b border-dark-border bg-white/95 backdrop-blur-sm"
    >
      <div className="mx-auto max-w-6xl px-4">
        <div className="flex min-h-16 items-center justify-between gap-3">
          <Link
            href="/dashboard"
            className="focus-ring flex shrink-0 items-center gap-2.5 text-base font-medium tracking-[-0.015em] text-dark-text transition-colors hover:text-brand-600"
          >
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-[#121317] text-white">
              <svg
                aria-hidden="true"
                className="h-4 w-4"
                fill="none"
                viewBox="0 0 24 24"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M12 4L5.5 19H8.8L10.2 15.5H13.8L15.2 19H18.5L12 4ZM11.1 12.8L12 10.4L12.9 12.8H11.1Z"
                  fill="currentColor"
                />
                <rect x="15.5" y="16.5" width="3.5" height="2.2" rx="0.6" fill="#1a73e8" />
              </svg>
            </span>
            <span>Aquila Buddy</span>
          </Link>

          <button
            aria-controls={mobileMenuId}
            aria-expanded={isMenuOpen}
            aria-label={isMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
            className="focus-ring inline-grid h-9 w-9 place-items-center rounded-md border border-dark-border text-dark-muted transition-colors hover:bg-dark-elevated hover:text-dark-text sm:hidden"
            onClick={() => setIsMenuOpen((open) => !open)}
            type="button"
          >
            {isMenuOpen ? (
              <X className="h-4 w-4" />
            ) : (
              <Menu className="h-4 w-4" />
            )}
          </button>

          <div className="hidden items-center gap-1 sm:flex">
            <NavItems isActive={isActive} />
            <LogoutButton onLogout={handleLogout} />
          </div>
        </div>

        <div
          className={`${isMenuOpen ? "grid" : "hidden"} gap-1.5 border-t border-dark-border py-3 sm:hidden`}
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
            className={`focus-ring flex items-center gap-1.5 rounded-md border text-sm font-medium transition-colors ${
              mobile ? "px-3 py-2" : "px-3 py-1.5"
            } ${
              active
                ? "border-[#121317] bg-[#121317] text-white"
                : "border-transparent text-dark-muted hover:border-dark-border hover:bg-dark-elevated hover:text-dark-text"
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
      className={`focus-ring flex items-center gap-1.5 rounded-md border border-transparent text-sm font-medium text-dark-muted transition-colors hover:border-[#c5221f]/20 hover:bg-[#fce8e6] hover:text-[#c5221f] ${
        mobile ? "px-3 py-2 text-left" : "px-3 py-1.5 sm:ml-2"
      }`}
      onClick={onLogout}
      type="button"
    >
      <LogOut className="h-4 w-4 shrink-0" />
      <span>로그아웃</span>
    </button>
  );
}
