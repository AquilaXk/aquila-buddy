"use client";

import type { AnchorHTMLAttributes, MouseEvent } from "react";
import { isTauriDesktop, openExternalUrl } from "@/lib/desktop";

type ExternalBrowserLinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & {
  href: string;
};

function isSafeExternalHref(rawHref: string): boolean {
  return /^https?:\/\/[^\s<>"'`]+$/i.test(rawHref.trim());
}

/**
 * Opens external links via the validated Tauri browser bridge in desktop builds
 * while preserving normal target="_blank" semantics on the web and failing closed
 * on non-HTTP(S) schemes.
 */
export function ExternalBrowserLink({
  children,
  className,
  href,
  onClick,
  rel,
  target,
  ...rest
}: ExternalBrowserLinkProps) {
  if (!isSafeExternalHref(href)) {
    return <span className={className}>{children}</span>;
  }

  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    onClick?.(event);
    if (event.defaultPrevented || !href) {
      return;
    }
    if (isTauriDesktop()) {
      event.preventDefault();
      void openExternalUrl(href).catch(() => {});
    }
  }

  return (
    <a
      className={className}
      href={href}
      onClick={handleClick}
      rel={rel ?? "noopener noreferrer"}
      target={target ?? "_blank"}
      {...rest}
    >
      {children}
    </a>
  );
}
