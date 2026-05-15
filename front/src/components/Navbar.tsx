import Link from "next/link";

const links = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/profile", label: "Profile" },
  { href: "/issues", label: "Issues" },
  { href: "/pr-draft", label: "PR Draft" }
];

export function Navbar() {
  return (
    <header className="border-b border-line bg-white">
      <nav className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-3">
        <Link href="/" className="focus-ring rounded-md text-base font-bold text-ink">
          Aquila Buddy
        </Link>
        <div className="flex flex-wrap gap-2 text-sm">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="focus-ring rounded-md px-3 py-2 font-semibold text-neutral-700 hover:bg-neutral-100"
            >
              {link.label}
            </Link>
          ))}
        </div>
      </nav>
    </header>
  );
}
