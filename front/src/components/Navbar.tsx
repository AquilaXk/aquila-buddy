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
    router.replace("/login");
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
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
        <Link
          href="/dashboard"
          className="focus-ring flex items-center gap-3 text-lg font-bold text-github-text transition-colors hover:text-github-purple-light"
        >
          <span className="grid h-9 w-9 place-items-center rounded-md border border-github-accent/70 bg-github-bg shadow-inner shadow-black/40">
            <img alt="" className="h-7 w-7 object-contain" height="28" src="/brand-mascot-bank.png" width="28" />
          </span>
          <span>aquila buddy</span>
        </Link>

        <div className="flex items-center gap-1">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              className={`focus-ring rounded-md border px-3 py-1.5 text-sm font-semibold transition-colors ${
                isActive(link)
                  ? "border-github-accent bg-github-accent text-github-bg shadow-inner shadow-black/25"
                  : "border-transparent text-github-muted hover:border-github-border hover:bg-github-bg hover:text-github-text"
              }`}
              href={link.href}
            >
              {link.label}
            </Link>
          ))}
          <button
            className="ml-3 rounded-md border border-transparent px-3 py-1.5 text-sm font-semibold text-github-muted transition-colors hover:border-red-400/30 hover:bg-red-500/10 hover:text-red-400"
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
