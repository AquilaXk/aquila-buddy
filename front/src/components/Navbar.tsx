"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { clearToken } from "@/lib/auth";

const navLinks = [
  { href: "/dashboard", label: "대시보드" },
  { href: "/issues", label: "추천 이슈" },
  { href: "/pr-draft", label: "PR 초안", matchPrefixes: ["/pr-draft", "/pr/"] },
  { href: "/profile", label: "내 프로필" }
];

export default function Navbar() {
  const pathname = usePathname();
  const router = useRouter();

  function handleLogout() {
    clearToken();
    router.push("/login");
  }

  function isActive(link: (typeof navLinks)[number]) {
    if (link.matchPrefixes) {
      return link.matchPrefixes.some((prefix) => pathname.startsWith(prefix));
    }
    return pathname.startsWith(link.href);
  }

  return (
    <nav className="sticky top-0 z-50 border-b border-github-border bg-github-surface">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <Link
          href="/dashboard"
          className="flex items-center gap-2 text-lg font-bold tracking-tight text-github-purple-light transition-opacity hover:opacity-80"
        >
          <img alt="" className="h-7 w-7 rounded-md" height="28" src="/brand-mascot.png" width="28" />
          aquila buddy
        </Link>

        <div className="flex items-center gap-1">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              className={`rounded-md px-3 py-1.5 text-sm transition-colors ${
                isActive(link)
                  ? "bg-github-purple/20 text-github-purple-light"
                  : "text-github-muted hover:bg-white/5 hover:text-github-text"
              }`}
              href={link.href}
            >
              {link.label}
            </Link>
          ))}
          <button
            className="ml-3 rounded-md px-3 py-1.5 text-sm text-github-muted transition-colors hover:bg-red-500/10 hover:text-red-400"
            onClick={handleLogout}
            type="button"
          >
            로그아웃
          </button>
        </div>
      </div>
    </nav>
  );
}
