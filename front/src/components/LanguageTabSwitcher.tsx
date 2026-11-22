"use client";

import { useId, useRef, type KeyboardEvent } from "react";
import { cn } from "@/lib/utils";

type LanguageTabSwitcherProps = {
  activeTab: "ko" | "en";
  hasTranslation: boolean;
  disabled?: boolean;
  tabIds?: { ko: string; en: string };
  panelIds?: { ko: string; en: string };
  onSelectTab: (tab: "ko" | "en") => void;
};

export function LanguageTabSwitcher({
  activeTab,
  hasTranslation,
  disabled = false,
  tabIds,
  panelIds,
  onSelectTab,
}: LanguageTabSwitcherProps) {
  const generatedId = useId();
  const koRef = useRef<HTMLButtonElement>(null);
  const enRef = useRef<HTMLButtonElement>(null);

  const koTabId = tabIds?.ko ?? `${generatedId}-tab-ko`;
  const enTabId = tabIds?.en ?? `${generatedId}-tab-en`;

  function focusAndSelect(nextTab: "ko" | "en") {
    if (disabled) {
      return;
    }
    onSelectTab(nextTab);
    if (nextTab === "ko") {
      koRef.current?.focus();
    } else {
      enRef.current?.focus();
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLButtonElement>, current: "ko" | "en") {
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      focusAndSelect(current === "ko" ? "en" : "ko");
    } else if (event.key === "Home") {
      event.preventDefault();
      focusAndSelect("ko");
    } else if (event.key === "End") {
      event.preventDefault();
      focusAndSelect("en");
    }
  }

  return (
    <div
      aria-label="언어 전환"
      className="inline-flex items-center gap-0.5 rounded-md border border-[#d1d9e0] bg-[#eaeef2] p-0.5"
      role="tablist"
    >
      <button
        aria-controls={panelIds?.ko}
        aria-selected={activeTab === "ko"}
        className={cn(
          "focus-ring rounded-[5px] px-2.5 py-1 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-50",
          activeTab === "ko"
            ? "border border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
            : "border border-transparent font-medium text-[#59636e] hover:text-[#1f2328]"
        )}
        disabled={disabled}
        id={koTabId}
        onClick={() => onSelectTab("ko")}
        onKeyDown={(event) => handleKeyDown(event, "ko")}
        ref={koRef}
        role="tab"
        tabIndex={activeTab === "ko" ? 0 : -1}
        type="button"
      >
        한국어 원본
      </button>
      <button
        aria-controls={panelIds?.en}
        aria-selected={activeTab === "en"}
        className={cn(
          "focus-ring rounded-[5px] px-2.5 py-1 text-xs transition-colors disabled:cursor-not-allowed disabled:opacity-50",
          activeTab === "en"
            ? "border border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
            : "border border-transparent font-medium text-[#59636e] hover:text-[#1f2328]"
        )}
        disabled={disabled}
        id={enTabId}
        onClick={() => onSelectTab("en")}
        onKeyDown={(event) => handleKeyDown(event, "en")}
        ref={enRef}
        role="tab"
        tabIndex={activeTab === "en" ? 0 : -1}
        type="button"
      >
        English {hasTranslation ? "✓" : ""}
      </button>
    </div>
  );
}
