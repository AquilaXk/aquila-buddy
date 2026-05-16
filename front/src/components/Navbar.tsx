"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { clearToken } from "@/lib/auth";

const links = [
  { href: "/dashboard", label: "대시보드" },
  { href: "/issues", label: "추천 이슈" },
  { href: "/pr-draft", label: "PR 초안", matchPrefixes: ["/pr-draft", "/pr/"] },
  { href: "/profile", label: "내 프로필" }
];

export function Navbar() {
  const pathname = usePathname();
  const router = useRouter();

  function handleLogout() {
    clearToken();
    router.push("/login");
  }

  function isActive(link: (typeof links)[number]) {
    if (link.matchPrefixes) {
      return link.matchPrefixes.some((prefix) => pathname.startsWith(prefix));
    }

    return pathname.startsWith(link.href);
  }

  return (
    <header className="sticky top-0 z-50 border-b border-github-border bg-github-surface">
      <nav className="mx-auto flex h-14 max-w-6xl flex-wrap items-center justify-between gap-3 px-4">
        <Link
          href="/dashboard"
          className="focus-ring rounded-md text-lg font-bold tracking-normal text-github-purple-light hover:opacity-80"
        >
          OMOS
        </Link>

        <div className="flex flex-wrap items-center gap-1 text-sm">
          {links.map((link) => (
            <Link
              key={link.href}
              className={`focus-ring rounded-md px-3 py-1.5 font-medium transition-colors ${
                isActive(link)
                  ? "bg-github-purple/20 text-github-purple-light"
                  : "text-github-muted hover:bg-github-surface/5 hover:text-github-text"
              }`}
              href={link.href}
            >
              {link.label}
            </Link>
          ))}
          <button
            className="focus-ring ml-2 rounded-md px-3 py-1.5 font-medium text-github-muted transition-colors hover:bg-red-500/10 hover:text-red-400"
            onClick={handleLogout}
            type="button"
          >
            로그아웃
          </button>
        </div>
      </nav>
    </header>
  );
}
