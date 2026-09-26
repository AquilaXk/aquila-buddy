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
    <div className="flex items-center gap-1 rounded-xl border border-dark-border bg-dark-scaffold p-1">
      <button
        className={`focus-ring rounded-lg px-3 py-1 text-xs font-semibold transition-all ${
          activeTab === "ko"
            ? "bg-brand-500 text-white shadow-sm"
            : "text-dark-muted hover:text-dark-text"
        } disabled:cursor-not-allowed disabled:opacity-50`}
        disabled={disabled}
        onClick={() => onSelectTab("ko")}
        type="button"
      >
        한국어 원본
      </button>
      <button
        className={`focus-ring rounded-lg px-3 py-1 text-xs font-semibold transition-all ${
          activeTab === "en"
            ? "bg-brand-500 text-white shadow-sm"
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
