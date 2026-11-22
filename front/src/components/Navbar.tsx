"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  CircleDot,
  GitPullRequest,
  HelpCircle,
  Home,
  LogOut,
  Menu,
  PanelLeft,
  Search,
  User,
  X,
} from "./icons";
import { OPEN_COMMAND_PALETTE_EVENT } from "./CommandPalette";
import { OPEN_SHORTCUT_HELP_EVENT } from "./ShortcutHelpDialog";
import { Kbd } from "./ui/kbd";
import { getUserProfile, logout, type UserProfile } from "@/lib/api";
import { authStateChangedEvent, clearAuthState, isLoggedIn } from "@/lib/auth";
import { vaultDeleteToken } from "@/lib/desktop";
import { cn } from "@/lib/utils";

const SIDEBAR_STORAGE_KEY = "aquila-buddy-sidebar-collapsed";
const mobileMenuId = "primary-navigation-drawer";

const navLinks = [
  { href: "/dashboard", label: "대시보드", matchPrefixes: ["/dashboard"], icon: Home },
  { href: "/issues", label: "추천 이슈", matchPrefixes: ["/issues"], icon: CircleDot },
  { href: "/pr-draft", label: "PR 초안", matchPrefixes: ["/pr-draft", "/pr/"], icon: GitPullRequest },
  { href: "/profile", label: "내 프로필", matchPrefixes: ["/profile"], icon: User },
];

type SidebarContextValue = {
  collapsed: boolean;
  toggleCollapsed: () => void;
  mobileOpen: boolean;
  setMobileOpen: (open: boolean) => void;
};

const SidebarContext = createContext<SidebarContextValue>({
  collapsed: false,
  toggleCollapsed: () => {},
  mobileOpen: false,
  setMobileOpen: () => {},
});

export function SidebarProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    try {
      const saved = window.localStorage.getItem(SIDEBAR_STORAGE_KEY);
      if (saved === "true") {
        setCollapsed(true);
      }
    } catch {
      // 스토리지 접근 불가 환경에서는 기본 펼침 상태를 유지한다.
    }
  }, []);

  useEffect(() => {
    setMobileOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!mobileOpen) {
      return;
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setMobileOpen(false);
      }
    }
    window.addEventListener("keydown", handleEscape);
    return () => window.removeEventListener("keydown", handleEscape);
  }, [mobileOpen]);

  const toggleCollapsed = useCallback(() => {
    setCollapsed((prev) => {
      const next = !prev;
      try {
        window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(next));
      } catch {
        // 스토리지 접근 불가 환경에서는 메모리 상태만 갱신한다.
      }
      return next;
    });
  }, []);

  return (
    <SidebarContext.Provider value={{ collapsed, toggleCollapsed, mobileOpen, setMobileOpen }}>
      {children}
    </SidebarContext.Provider>
  );
}

function resolveRouteTitle(pathname: string): string {
  if (pathname === "/dashboard" || pathname.startsWith("/dashboard/")) {
    return "대시보드";
  }
  if (pathname === "/issues" || pathname === "/issues/") {
    return "추천 이슈";
  }
  if (pathname.startsWith("/issues/")) {
    return "이슈 상세 분석";
  }
  if (pathname === "/pr-draft" || pathname.startsWith("/pr-draft/")) {
    return "PR 초안";
  }
  if (pathname.startsWith("/pr/")) {
    return "PR 초안 상세";
  }
  if (pathname === "/profile" || pathname.startsWith("/profile/")) {
    return "내 프로필";
  }
  return "워크스페이스";
}

export function SiteHeader() {
  const pathname = usePathname();
  const { collapsed, toggleCollapsed, mobileOpen, setMobileOpen } = useContext(SidebarContext);
  const routeTitle = resolveRouteTitle(pathname);

  return (
    <header
      className="sticky top-0 z-30 flex h-12 shrink-0 items-center justify-between gap-3 border-b border-[#d1d9e0] bg-[#ffffff]/95 px-4 backdrop-blur lg:rounded-t-xl"
      data-tauri-drag-region
    >
      <div className="flex min-w-0 items-center gap-2.5">
        <button
          aria-expanded={!collapsed}
          aria-label={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
          className="focus-ring hidden h-7 w-7 items-center justify-center rounded-md text-[#59636e] transition-colors hover:bg-[#f6f8fa] hover:text-[#1f2328] lg:inline-flex"
          onClick={toggleCollapsed}
          title={collapsed ? "사이드바 펼치기" : "사이드바 접기"}
          type="button"
        >
          <PanelLeft className="h-4 w-4" aria-hidden="true" />
        </button>

        <button
          aria-controls={mobileMenuId}
          aria-expanded={mobileOpen}
          aria-label={mobileOpen ? "메뉴 닫기" : "메뉴 열기"}
          className="focus-ring inline-flex h-8 w-8 items-center justify-center rounded-md text-[#59636e] transition-colors hover:bg-[#f6f8fa] hover:text-[#1f2328] lg:hidden"
          onClick={() => setMobileOpen(!mobileOpen)}
          type="button"
        >
          {mobileOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
        </button>

        <span aria-hidden="true" className="h-4 w-px shrink-0 bg-[#d1d9e0]" />

        <div className="flex min-w-0 items-center gap-2 text-[13px]">
          <Link
            className="hidden font-medium text-[#59636e] transition-colors hover:text-[#1f2328] sm:inline"
            href="/dashboard"
          >
            Aquila Buddy
          </Link>
          <span aria-hidden="true" className="hidden text-[#d1d9e0] sm:inline">/</span>
          <span className="truncate font-semibold text-[#1f2328]">{routeTitle}</span>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <button
          aria-label="빠른 탐색 열기"
          className="focus-ring inline-flex h-7 items-center gap-1.5 rounded-md border border-[#d1d9e0] bg-[#f6f8fa] px-2 text-xs font-medium text-[#59636e] transition-colors hover:bg-[#eff2f5] hover:text-[#1f2328] lg:hidden"
          onClick={() => window.dispatchEvent(new CustomEvent(OPEN_COMMAND_PALETTE_EVENT))}
          type="button"
        >
          <Search className="h-3.5 w-3.5" aria-hidden="true" />
          <span className="hidden sm:inline">빠른 탐색</span>
          <Kbd className="hidden sm:inline-flex">⌘K</Kbd>
        </button>

        <button
          aria-label="키보드 단축키 도움말"
          className="focus-ring inline-flex h-7 items-center gap-1.5 rounded-md border border-[#d1d9e0] bg-[#ffffff] px-2 text-xs font-medium text-[#59636e] transition-colors hover:bg-[#f6f8fa] hover:text-[#1f2328]"
          onClick={() => window.dispatchEvent(new CustomEvent(OPEN_SHORTCUT_HELP_EVENT))}
          title="키보드 단축키 (?)"
          type="button"
        >
          <HelpCircle className="h-3.5 w-3.5" aria-hidden="true" />
          <Kbd>?</Kbd>
        </button>
      </div>
    </header>
  );
}

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();
  const { collapsed, mobileOpen, setMobileOpen } = useContext(SidebarContext);
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
    setMobileOpen(false);
    clearAuthState();
    await vaultDeleteToken().catch(() => {});

    try {
      await logout();
    } catch {
      // 서버 쿠키가 이미 만료된 경우에도 로컬 세션 상태를 정리하고 로그인 화면으로 이동한다.
    }

    router.replace("/login");
  }

  const displayHandle = profile?.login ? `@${profile.login}` : profile?.name || "내 계정";
  const initial = (profile?.name || profile?.login || "A").at(0)?.toUpperCase() ?? "A";
  const showAvatarImage = Boolean(profile?.avatarUrl) && !avatarBroken;

  return (
    <>
      {mobileOpen ? (
        <button
          aria-label="내비게이션 닫기"
          className="fixed inset-0 z-40 bg-[#1f2328]/40 lg:hidden"
          onClick={() => setMobileOpen(false)}
          type="button"
        />
      ) : null}

      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-50 flex flex-col border-r border-[#d1d9e0] bg-[#f6f8fa] transition-[width,transform] duration-150 lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:shrink-0 lg:translate-x-0 lg:border-r-0 lg:bg-transparent",
          mobileOpen ? "w-[248px] translate-x-0 shadow-xl" : "w-[248px] -translate-x-full",
          collapsed ? "lg:w-[60px]" : "lg:w-[236px]"
        )}
        id={mobileMenuId}
      >
        <div className={cn("flex h-full flex-col p-2.5", collapsed && "lg:px-2")}>
          <div className={cn("flex items-center justify-between gap-2 px-1.5 py-2", collapsed && "lg:justify-center lg:px-0")}>
            <Link
              className="focus-ring flex min-w-0 items-center gap-2.5 rounded-md px-1 py-0.5 text-[#1f2328] transition-colors hover:bg-[#eaeef2]/70"
              href="/dashboard"
              onClick={() => setMobileOpen(false)}
              title="Aquila Buddy"
            >
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-md bg-[#1f2328] text-white shadow-xs">
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
                  <rect x="15.5" y="16.5" width="3.5" height="2.2" rx="0.6" fill="#0969da" />
                </svg>
              </span>
              <span
                className={cn(
                  "truncate text-[14px] font-semibold tracking-[-0.02em] text-[#1f2328]",
                  collapsed && "lg:hidden"
                )}
              >
                Aquila Buddy
              </span>
            </Link>

            <button
              aria-label="메뉴 닫기"
              className="focus-ring inline-flex h-7 w-7 items-center justify-center rounded-md text-[#59636e] hover:bg-[#eaeef2] hover:text-[#1f2328] lg:hidden"
              onClick={() => setMobileOpen(false)}
              type="button"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="mt-2">
            <button
              aria-label="빠른 탐색 열기"
              className={cn(
                "focus-ring flex w-full items-center justify-between gap-2 rounded-md border border-[#d1d9e0] bg-[#ffffff] px-2.5 py-1.5 text-[12.5px] font-medium text-[#59636e] shadow-xs transition-colors hover:border-[#b9c3ce] hover:text-[#1f2328]",
                collapsed && "lg:justify-center lg:px-0"
              )}
              onClick={() => {
                setMobileOpen(false);
                window.dispatchEvent(new CustomEvent(OPEN_COMMAND_PALETTE_EVENT));
              }}
              title="빠른 탐색 (⌘K)"
              type="button"
            >
              <span className="flex items-center gap-2">
                <Search className="h-3.5 w-3.5 shrink-0 text-[#59636e]" aria-hidden="true" />
                <span className={cn(collapsed && "lg:hidden")}>빠른 탐색</span>
              </span>
              <Kbd className={cn(collapsed && "lg:hidden")}>⌘K</Kbd>
            </button>
          </div>

          <nav aria-label="Primary" className="mt-4 flex flex-1 flex-col gap-1">
            {navLinks.map((link) => {
              const active = isActiveLink(link.matchPrefixes);
              const Icon = link.icon;
              return (
                <Link
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "focus-ring flex items-center gap-2.5 rounded-md border px-2.5 py-2 text-[13px] transition-colors",
                    collapsed && "lg:justify-center lg:px-0",
                    active
                      ? "border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
                      : "border-transparent font-medium text-[#59636e] hover:bg-[#eaeef2]/70 hover:text-[#1f2328]"
                  )}
                  href={link.href}
                  key={link.href}
                  onClick={() => setMobileOpen(false)}
                  title={collapsed ? link.label : undefined}
                >
                  <Icon
                    aria-hidden="true"
                    className={cn(
                      "h-4 w-4 shrink-0",
                      active ? "text-[#0969da]" : "text-[#59636e]"
                    )}
                  />
                  <span className={cn("truncate", collapsed && "lg:hidden")}>{link.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="mt-auto space-y-1 border-t border-[#d1d9e0] pt-3">
            <button
              aria-label="키보드 단축키"
              className={cn(
                "focus-ring flex w-full items-center justify-between gap-2 rounded-md px-2.5 py-1.5 text-[12.5px] font-medium text-[#59636e] transition-colors hover:bg-[#eaeef2]/70 hover:text-[#1f2328]",
                collapsed && "lg:justify-center lg:px-0"
              )}
              onClick={() => {
                setMobileOpen(false);
                window.dispatchEvent(new CustomEvent(OPEN_SHORTCUT_HELP_EVENT));
              }}
              title="단축키 (?)"
              type="button"
            >
              <span className="flex items-center gap-2">
                <HelpCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
                <span className={cn(collapsed && "lg:hidden")}>단축키</span>
              </span>
              <Kbd className={cn(collapsed && "lg:hidden")}>?</Kbd>
            </button>

            <Link
              aria-label="내 프로필"
              className={cn(
                "focus-ring flex items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[12.5px] font-medium text-[#1f2328] transition-colors hover:bg-[#eaeef2]/70",
                collapsed && "lg:justify-center lg:px-0"
              )}
              href="/profile"
              onClick={() => setMobileOpen(false)}
              title={displayHandle}
            >
              {showAvatarImage ? (
                <img
                  alt={displayHandle}
                  className="h-5 w-5 shrink-0 rounded-full border border-[#d1d9e0] object-cover"
                  onError={() => setAvatarBroken(true)}
                  referrerPolicy="no-referrer"
                  src={profile?.avatarUrl ?? undefined}
                />
              ) : (
                <span className="grid h-5 w-5 shrink-0 place-items-center rounded-full border border-[#0969da]/30 bg-[#ddf4ff] text-[10px] font-semibold text-[#0969da]">
                  {initial}
                </span>
              )}
              <span className={cn("truncate font-mono text-xs text-[#1f2328]", collapsed && "lg:hidden")}>
                {displayHandle}
              </span>
            </Link>

            <button
              className={cn(
                "focus-ring flex w-full items-center gap-2.5 rounded-md px-2.5 py-1.5 text-[12.5px] font-medium text-[#59636e] transition-colors hover:bg-[#eaeef2]/70 hover:text-[#d1242f]",
                collapsed && "lg:justify-center lg:px-0"
              )}
              onClick={handleLogout}
              title={collapsed ? "로그아웃" : undefined}
              type="button"
            >
              <LogOut className="h-4 w-4 shrink-0" aria-hidden="true" />
              <span className={cn(collapsed && "lg:hidden")}>로그아웃</span>
            </button>
          </div>
        </div>
      </aside>
    </>
  );
}
