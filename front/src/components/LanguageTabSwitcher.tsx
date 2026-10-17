"use client";

type LanguageTabSwitcherProps = {
  activeTab: "ko" | "en";
  hasTranslation: boolean;
  disabled?: boolean;
  onSelectTab: (tab: "ko" | "en") => void;
};

export function LanguageTabSwitcher({
  activeTab,
  hasTranslation,
  disabled = false,
  onSelectTab
}: LanguageTabSwitcherProps) {
  return (
    <div className="flex items-center gap-1 rounded-lg border border-dark-border bg-dark-elevated p-1">
      <button
        className={`focus-ring rounded-md px-3 py-1 text-xs font-medium transition-colors ${
          activeTab === "ko"
            ? "bg-[#121317] text-white"
            : "text-dark-muted hover:text-dark-text"
        } disabled:cursor-not-allowed disabled:opacity-50`}
        disabled={disabled}
        onClick={() => onSelectTab("ko")}
        type="button"
      >
        한국어 원본
      </button>
      <button
        className={`focus-ring rounded-md px-3 py-1 text-xs font-medium transition-colors ${
          activeTab === "en"
            ? "bg-[#121317] text-white"
            : "text-dark-muted hover:text-dark-text"
        } disabled:cursor-not-allowed disabled:opacity-50`}
        disabled={disabled}
        onClick={() => onSelectTab("en")}
        type="button"
      >
        English {hasTranslation ? "✓" : ""}
      </button>
    </div>
  );
}
