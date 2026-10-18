"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  LogOut,
  Menu,
  X,
} from "lucide-react";
import { getUserProfile, logout, type UserProfile } from "@/lib/api";
import { authStateChangedEvent, clearAuthState, isLoggedIn } from "@/lib/auth";

const navLinks = [
  { href: "/dashboard", label: "대시보드", matchPrefixes: ["/dashboard"] },
  { href: "/issues", label: "추천 이슈", matchPrefixes: ["/issues"] },
  { href: "/pr-draft", label: "PR 초안", matchPrefixes: ["/pr-draft", "/pr/"] },
  { href: "/profile", label: "내 프로필", matchPrefixes: ["/profile"] },
];

const mobileMenuId = "primary-navigation-drawer";

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [avatarBroken, setAvatarBroken] = useState(false);

  function isActiveLink(matchPrefixes: string[]) {
    return matchPrefixes.some((prefix) => {
      if (prefix.endsWith("/")) {
        return pathname.startsWith(prefix);
      }

      return pathname === prefix || pathname.startsWith(`${prefix}/`);
    });
  }

  useEffect(() => {
    setIsMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    let cancelled = false;

    async function loadNavbarProfile() {
      if (!isLoggedIn()) {
        setProfile(null);
        return;
      }

      try {
        const currentProfile = await getUserProfile();
        if (!cancelled) {
          setProfile(currentProfile);
          setAvatarBroken(false);
        }
      } catch {
        if (!cancelled) {
          setProfile(null);
        }
      }
    }

    loadNavbarProfile();
    window.addEventListener(authStateChangedEvent, loadNavbarProfile);

    return () => {
      cancelled = true;
      window.removeEventListener(authStateChangedEvent, loadNavbarProfile);
    };
  }, [pathname]);

  async function handleLogout() {
    setIsMenuOpen(false);
    clearAuthState();

    try {
      await logout();
    } catch {
      // Clear local session state and redirect even when the server cookie is already gone.
    }

    router.replace("/login");
  }

  const displayHandle = profile?.login ? `@${profile.login}` : profile?.name || "내 계정";
  const initial = (profile?.name || profile?.login || "A").at(0)?.toUpperCase() ?? "A";
  const showAvatarImage = Boolean(profile?.avatarUrl) && !avatarBroken;

  return (
    <nav
      aria-label="Primary"
      className="sticky top-0 z-50 border-b border-dark-border bg-white/95 backdrop-blur-sm"
    >
      <div className="mx-auto flex min-h-16 max-w-6xl items-center justify-between gap-6 px-4 sm:min-h-14">
        <div className="flex items-center gap-8">
          <Link
            href="/dashboard"
            className="focus-ring flex items-center gap-2.5 rounded-sm py-1 text-ink-primary transition-opacity hover:opacity-80"
          >
            <span className="grid h-6 w-6 shrink-0 place-items-center rounded bg-[#121317] text-white">
              <svg
                aria-hidden="true"
                className="h-3.5 w-3.5"
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
            <span className="button-text text-sm font-semibold tracking-[-0.02em] text-ink-primary">
              Aquila Buddy
            </span>
          </Link>

          <div className="hidden items-center gap-1 sm:flex">
            {navLinks.map((link) => {
              const active = isActiveLink(link.matchPrefixes);
              return (
                <Link
                  aria-current={active ? "page" : undefined}
                  key={link.href}
                  href={link.href}
                  className={`focus-ring relative flex h-14 items-center px-3 text-[13px] transition-colors ${
                    active
                      ? "border-b-2 border-[#121317] font-medium text-ink-primary"
                      : "border-b-2 border-transparent text-ink-secondary hover:text-ink-primary"
                  }`}
                >
                  <span className="button-text">{link.label}</span>
                </Link>
              );
            })}
          </div>
        </div>

        <div className="flex items-center gap-3">
          <button
            aria-controls={mobileMenuId}
            aria-expanded={isMenuOpen}
            aria-label={isMenuOpen ? "메뉴 닫기" : "메뉴 열기"}
            className="focus-ring inline-flex h-8 w-8 items-center justify-center rounded text-ink-secondary transition-colors hover:bg-neutral-hover hover:text-ink-primary sm:hidden"
            onClick={() => setIsMenuOpen((open) => !open)}
            type="button"
          >
            {isMenuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
          </button>

          <Link
            aria-label="내 프로필"
            className="focus-ring hidden items-center gap-2 rounded px-2 py-1 text-xs font-medium text-ink-secondary transition-colors hover:bg-neutral-hover hover:text-ink-primary sm:inline-flex"
            href="/profile"
            title={displayHandle}
          >
            {showAvatarImage ? (
              <img
                alt={displayHandle}
                className="h-5 w-5 rounded-full object-cover"
                onError={() => setAvatarBroken(true)}
                referrerPolicy="no-referrer"
                src={profile?.avatarUrl ?? undefined}
              />
            ) : (
              <span className="grid h-5 w-5 place-items-center rounded-full bg-neutral-hover text-[10px] font-semibold text-ink-primary">
                {initial}
              </span>
            )}
            <span className="max-w-32 truncate font-mono text-xs text-ink-primary">{displayHandle}</span>
          </Link>

          <span aria-hidden="true" className="hidden h-3.5 w-px bg-neutral-border sm:inline-block" />

          <button
            onClick={handleLogout}
            type="button"
            className="focus-ring hidden items-center gap-1.5 rounded px-2 py-1 text-xs font-medium text-ink-muted transition-colors hover:bg-neutral-hover hover:text-ink-primary sm:inline-flex"
          >
            <LogOut className="h-3.5 w-3.5" />
            <span className="button-text">로그아웃</span>
          </button>
        </div>
      </div>

      {isMenuOpen ? (
        <div
          className="border-t border-dark-border bg-white px-4 py-2.5 sm:hidden"
          id={mobileMenuId}
        >
          <div className="flex flex-col divide-y divide-neutral-border">
            {navLinks.map((link) => {
              const active = isActiveLink(link.matchPrefixes);
              return (
                <Link
                  aria-current={active ? "page" : undefined}
                  key={link.href}
                  href={link.href}
                  onClick={() => setIsMenuOpen(false)}
                  className={`focus-ring flex items-center justify-between py-2.5 text-sm font-medium transition-colors ${
                    active
                      ? "text-ink-primary"
                      : "text-ink-secondary hover:text-ink-primary"
                  }`}
                >
                  <span>{link.label}</span>
                  {active ? <span className="h-1.5 w-1.5 rounded-full bg-[#121317]" /> : null}
                </Link>
              );
            })}
            <button
              onClick={handleLogout}
              type="button"
              className="focus-ring flex items-center gap-2 py-2.5 text-left text-sm font-medium text-ink-muted transition-colors hover:text-ink-primary"
            >
              <LogOut className="h-4 w-4" />
              <span>로그아웃</span>
            </button>
          </div>
        </div>
      ) : null}
    </nav>
  );
}
