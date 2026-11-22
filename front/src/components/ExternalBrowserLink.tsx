"use client";

import type { AnchorHTMLAttributes, MouseEvent } from "react";
import { isTauriDesktop, openExternalUrl } from "@/lib/desktop";

type ExternalBrowserLinkProps = AnchorHTMLAttributes<HTMLAnchorElement> & {
  href: string;
};

/**
 * Opens external links via the validated Tauri browser bridge in desktop builds
 * while preserving normal target="_blank" semantics on the web.
 */
export function ExternalBrowserLink({
  children,
  href,
  onClick,
  rel,
  target,
  ...rest
}: ExternalBrowserLinkProps) {
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
