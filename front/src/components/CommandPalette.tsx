"use client";

import { useCallback, useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { FileText, GitPullRequest, Keyboard, LayoutList, Search, User, X } from "./icons";
import { OPEN_SHORTCUT_HELP_EVENT } from "./ShortcutHelpDialog";
import { Kbd } from "./ui/kbd";
import {
  getPullRequestDraftHistory,
  getRecommendedIssueHistory,
  type RecommendedIssueHistory,
  type StoredPullRequestDraft,
} from "@/lib/api";
import { authStateChangedEvent, isLoggedIn } from "@/lib/auth";
import { bitapMatch } from "@/lib/bitapSearch";
import { issueDetailHref, prDetailHref } from "@/lib/routes";
import { cn } from "@/lib/utils";

export const OPEN_COMMAND_PALETTE_EVENT = "aquila-buddy-open-palette";

type PaletteItem = {
  id: string;
  group: "화면 이동" | "빠른 작업" | "추천 이슈" | "PR 초안";
  title: string;
  subtitle: string;
  href?: string;
  eventName?: string;
  keywords: string;
};

const staticNavItems: PaletteItem[] = [
  {
    id: "nav-dashboard",
    group: "화면 이동",
    title: "대시보드",
    subtitle: "요약 지표·최근 추천·PR 초안 개요",
    href: "/dashboard",
    keywords: "dashboard 개요 홈",
  },
  {
    id: "nav-issues",
    group: "화면 이동",
    title: "추천 이슈",
    subtitle: "맞춤 오픈소스 이슈 탐색 및 코드 맥락 분석",
    href: "/issues",
    keywords: "issues 추천 이슈 목록 검색",
  },
  {
    id: "nav-pr-draft",
    group: "화면 이동",
    title: "PR 초안",
    subtitle: "PR 초안 작성 및 생성 이력",
    href: "/pr-draft",
    keywords: "pr draft pull request 초안 작성",
  },
  {
    id: "nav-profile",
    group: "화면 이동",
    title: "내 프로필",
    subtitle: "기술 스택·관심 토픽·추천 기준 동기화",
    href: "/profile",
    keywords: "profile 설정 프로필 추천 기준 동기화",
  },
  {
    id: "action-shortcuts",
    group: "빠른 작업",
    title: "키보드 단축키 도움말 열기",
    subtitle: "현재 화면 및 전체 탐색 단축키 안내 (?)",
    eventName: OPEN_SHORTCUT_HELP_EVENT,
    keywords: "shortcut help 단축키 도움말 ?",
  },
];

export default function CommandPalette() {
  const router = useRouter();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const headingId = useId();
  const listboxId = useId();

  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [activeIndex, setActiveIndex] = useState(0);
  const [issues, setIssues] = useState<RecommendedIssueHistory[]>([]);
  const [drafts, setDrafts] = useState<StoredPullRequestDraft[]>([]);

  const openPalette = useCallback(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    setQuery("");
    setActiveIndex(0);
    setOpen(true);
    if (!dialog.open) {
      dialog.showModal();
    }
    inputRef.current?.focus();
  }, []);

  const closePalette = useCallback(() => {
    const dialog = dialogRef.current;
    if (dialog?.open) {
      dialog.close();
    }
    setOpen(false);
  }, []);

  useEffect(() => {
    function handleAuthStateChanged() {
      if (!isLoggedIn()) {
        setIssues([]);
        setDrafts([]);
      }
    }

    window.addEventListener(authStateChangedEvent, handleAuthStateChanged);
    return () => {
      window.removeEventListener(authStateChangedEvent, handleAuthStateChanged);
    };
  }, []);

  useEffect(() => {
    if (!open || !isLoggedIn()) {
      return;
    }
    let cancelled = false;

    Promise.allSettled([
      getRecommendedIssueHistory(),
      getPullRequestDraftHistory(0, 5),
    ]).then(([issueRes, draftRes]) => {
      if (cancelled) {
        return;
      }
      if (issueRes.status === "fulfilled") {
        setIssues(issueRes.value.slice(0, 12));
      }
      if (draftRes.status === "fulfilled") {
        setDrafts(draftRes.value.content.slice(0, 8));
      }
    });

    return () => {
      cancelled = true;
    };
  }, [open]);

  useEffect(() => {
    function handleGlobalKeyDown(event: globalThis.KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (dialogRef.current?.open) {
          closePalette();
        } else {
          openPalette();
        }
      }
    }

    function handleCustomOpen() {
      openPalette();
    }

    window.addEventListener("keydown", handleGlobalKeyDown);
    window.addEventListener(OPEN_COMMAND_PALETTE_EVENT, handleCustomOpen);
    return () => {
      window.removeEventListener("keydown", handleGlobalKeyDown);
      window.removeEventListener(OPEN_COMMAND_PALETTE_EVENT, handleCustomOpen);
    };
  }, [closePalette, openPalette]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }

    function handleDialogClose() {
      if (dialog) {
        dialog.returnValue = "";
      }
      setOpen(false);
    }

    dialog.addEventListener("close", handleDialogClose);

    if (typeof HTMLDialogElement !== "undefined" && !("closedBy" in HTMLDialogElement.prototype)) {
      let pressedOutside = false;
      const isOutside = (e: PointerEvent) => {
        const rect = dialog.getBoundingClientRect();
        return (
          e.clientX < rect.left ||
          e.clientX > rect.right ||
          e.clientY < rect.top ||
          e.clientY > rect.bottom
        );
      };
      const onPointerDown = (e: PointerEvent) => {
        pressedOutside = isOutside(e);
      };
      const onPointerUp = (e: PointerEvent) => {
        if (pressedOutside && isOutside(e)) {
          closePalette();
        }
        pressedOutside = false;
      };
      dialog.addEventListener("pointerdown", onPointerDown);
      dialog.addEventListener("pointerup", onPointerUp);
      return () => {
        dialog.removeEventListener("close", handleDialogClose);
        dialog.removeEventListener("pointerdown", onPointerDown);
        dialog.removeEventListener("pointerup", onPointerUp);
      };
    }

    return () => {
      dialog.removeEventListener("close", handleDialogClose);
    };
  }, [closePalette]);

  const allItems = useMemo<PaletteItem[]>(() => {
    const issueItems: PaletteItem[] = issues.map((issue) => ({
      id: `issue-${issue.id}`,
      group: "추천 이슈",
      title: issue.title,
      subtitle: `${issue.repoFullName} #${issue.issueNumber}`,
      href: issueDetailHref(issue.id),
      keywords: `${issue.repoFullName} #${issue.issueNumber} ${issue.labels.join(" ")}`,
    }));

    const draftItems: PaletteItem[] = drafts.map((draft) => ({
      id: `draft-${draft.id}`,
      group: "PR 초안",
      title: draft.title,
      subtitle: `${draft.upstreamRepo} #${draft.githubIssueNumber}`,
      href: prDetailHref(draft.id),
      keywords: `${draft.upstreamRepo} ${draft.baseBranch} ${draft.headBranch}`,
    }));

    return [...staticNavItems, ...issueItems, ...draftItems];
  }, [drafts, issues]);

  const filteredItems = useMemo(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      return allItems;
    }

    const scored: Array<{ item: PaletteItem; rank: number }> = [];
    for (let i = 0; i < allItems.length; i++) {
      const item = allItems[i];
      const titleRank = bitapMatch(item.title, trimmed, 1);
      const subRank = titleRank === 2 ? 2 : bitapMatch(item.subtitle, trimmed, 1);
      const kwRank = Math.max(titleRank, subRank) === 2 ? 2 : bitapMatch(item.keywords, trimmed, 1);
      const best = Math.max(titleRank, subRank, kwRank);
      if (best > 0) {
        scored.push({ item, rank: best });
      }
    }
    scored.sort((a, b) => b.rank - a.rank);
    return scored.map((entry) => entry.item);
  }, [allItems, query]);

  const clampedActiveIndex =
    filteredItems.length === 0 ? -1 : Math.min(activeIndex, filteredItems.length - 1);
  const activeOptionId =
    clampedActiveIndex >= 0 ? `${listboxId}-opt-${filteredItems[clampedActiveIndex].id}` : undefined;

  const navigateToItem = useCallback(
    (item: PaletteItem) => {
      closePalette();
      if (item.eventName) {
        window.dispatchEvent(new CustomEvent(item.eventName));
        return;
      }
      if (item.href) {
        router.push(item.href);
      }
    },
    [closePalette, router]
  );

  function handleInputKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      if (filteredItems.length > 0) {
        setActiveIndex((prev) => (prev + 1) % filteredItems.length);
      }
      return;
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      if (filteredItems.length > 0) {
        setActiveIndex((prev) => (prev - 1 + filteredItems.length) % filteredItems.length);
      }
      return;
    }
    if (event.key === "Enter") {
      if (event.nativeEvent.isComposing || event.keyCode === 229) {
        return;
      }
      event.preventDefault();
      if (clampedActiveIndex >= 0) {
        navigateToItem(filteredItems[clampedActiveIndex]);
      }
    }
  }

  return (
    <dialog
      aria-labelledby={headingId}
      className="studio-command-dialog"
      ref={dialogRef}
      {...({ closedby: "any" } as Record<string, unknown>)}
    >
      <div className="flex flex-col overflow-hidden rounded-xl border border-[#d1d9e0] bg-white shadow-xl">
        <div className="primer-box-header flex items-center justify-between px-4 py-2.5">
          <h2 className="text-[13px] font-semibold tracking-[-0.015em] text-[#1f2328]" id={headingId}>
            빠른 명령·탐색
          </h2>
          <button
            aria-label="빠른 탐색 닫기"
            className="focus-ring inline-flex h-6 w-6 items-center justify-center rounded-md text-[#59636e] hover:bg-[#eaeef2] hover:text-[#1f2328]"
            onClick={closePalette}
            type="button"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>

        <div className="flex items-center gap-2 border-b border-[#d1d9e0] bg-white px-3.5 py-2.5">
          <Search className="h-4 w-4 shrink-0 text-[#59636e]" />
          <label className="sr-only" htmlFor={`${listboxId}-input`}>
            화면, 추천 이슈, PR 초안 검색
          </label>
          <input
            aria-activedescendant={activeOptionId}
            aria-autocomplete="list"
            aria-controls={listboxId}
            aria-expanded={open}
            autoFocus
            className="w-full border-0 bg-transparent text-[13.5px] text-[#1f2328] placeholder:text-[#59636e] focus:outline-none"
            id={`${listboxId}-input`}
            onChange={(event) => {
              setQuery(event.target.value);
              setActiveIndex(0);
            }}
            onKeyDown={handleInputKeyDown}
            placeholder="화면, 저장소, 이슈 번호, PR 초안 검색..."
            ref={inputRef}
            role="combobox"
            type="text"
            value={query}
          />
          <Kbd className="hidden sm:inline-flex">ESC</Kbd>
        </div>

        <div
          aria-label="검색 결과"
          className="max-h-80 divide-y divide-[#d1d9e0]/60 overflow-y-auto"
          id={listboxId}
          role="listbox"
        >
          {filteredItems.length === 0 ? (
            <div className="px-4 py-8 text-center text-[13px] text-[#59636e]">
              일치하는 항목이 없습니다.
            </div>
          ) : (
            filteredItems.map((item, idx) => {
              const selected = idx === clampedActiveIndex;
              return (
                <button
                  aria-selected={selected}
                  className={cn(
                    "flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left text-xs transition-colors",
                    selected ? "bg-[#f0f6fc] text-[#1f2328]" : "bg-white text-[#1f2328] hover:bg-[#f6f8fa]"
                  )}
                  id={`${listboxId}-opt-${item.id}`}
                  key={item.id}
                  onClick={() => navigateToItem(item)}
                  onMouseEnter={() => setActiveIndex(idx)}
                  role="option"
                  type="button"
                >
                  <div className="flex min-w-0 items-center gap-2.5">
                    <ItemIcon group={item.group} />
                    <div className="min-w-0">
                      <p className="truncate text-[13px] font-semibold text-[#1f2328]">{item.title}</p>
                      <p className="truncate font-mono text-[11.5px] text-[#59636e]">{item.subtitle}</p>
                    </div>
                  </div>
                  <span className="shrink-0 rounded-full border border-[#d1d9e0] bg-[#f6f8fa] px-2 py-0.5 text-[11px] font-medium text-[#59636e]">
                    {item.group}
                  </span>
                </button>
              );
            })
          )}
        </div>

        <div className="flex items-center justify-between border-t border-[#d1d9e0] bg-[#f6f8fa] px-4 py-2 text-[11px] text-[#59636e]">
          <span>↑↓ 이동 · Enter 열기 · Esc 닫기</span>
          <Kbd>⌘K</Kbd>
        </div>
      </div>
    </dialog>
  );
}

function ItemIcon({ group }: { group: PaletteItem["group"] }) {
  if (group === "추천 이슈") {
    return <LayoutList className="h-3.5 w-3.5 shrink-0 text-[#0969da]" />;
  }
  if (group === "PR 초안") {
    return <GitPullRequest className="h-3.5 w-3.5 shrink-0 text-[#1a7f37]" />;
  }
  if (group === "화면 이동") {
    return <FileText className="h-3.5 w-3.5 shrink-0 text-[#59636e]" />;
  }
  if (group === "빠른 작업") {
    return <Keyboard className="h-3.5 w-3.5 shrink-0 text-[#59636e]" />;
  }
  return <User className="h-3.5 w-3.5 shrink-0 text-[#59636e]" />;
}
