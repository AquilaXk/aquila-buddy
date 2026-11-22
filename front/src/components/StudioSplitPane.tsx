"use client";

import { useCallback, useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { PanelLeftClose, PanelLeftOpen } from "./icons";
import { cn } from "@/lib/utils";

type StudioSplitPaneProps = {
  storageKey: string;
  defaultRatio?: number;
  minRatio?: number;
  maxRatio?: number;
  leftLabel?: string;
  leftPane: ReactNode;
  rightPane: ReactNode;
  className?: string;
};

type StoredSplitState = {
  ratio: number;
  collapsed: boolean;
};

function clampRatio(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, Math.round(value)));
}

function isEditableElement(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  if (!el) {
    return false;
  }
  return (
    el.tagName === "INPUT" ||
    el.tagName === "TEXTAREA" ||
    el.tagName === "SELECT" ||
    el.isContentEditable
  );
}

export function StudioSplitPane({
  storageKey,
  defaultRatio = 42,
  minRatio = 25,
  maxRatio = 70,
  leftLabel = "보조 패널",
  leftPane,
  rightPane,
  className = "",
}: StudioSplitPaneProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const rafRef = useRef<number | null>(null);
  const pendingRatioRef = useRef<number | null>(null);
  const [ratio, setRatio] = useState<number>(() => clampRatio(defaultRatio, minRatio, maxRatio));
  const [collapsed, setCollapsed] = useState(false);
  const [dragging, setDragging] = useState(false);

  const persistState = useCallback(
    (next: StoredSplitState) => {
      try {
        window.localStorage.setItem(`aquila-buddy-split:${storageKey}`, JSON.stringify(next));
      } catch {
        // 스토리지 접근 불가 환경에서는 메모리 상태만 유지한다.
      }
    },
    [storageKey]
  );

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(`aquila-buddy-split:${storageKey}`);
      if (!raw) {
        return;
      }
      const parsed = JSON.parse(raw) as Partial<StoredSplitState>;
      if (typeof parsed.ratio === "number" && Number.isFinite(parsed.ratio)) {
        setRatio(clampRatio(parsed.ratio, minRatio, maxRatio));
      }
      if (typeof parsed.collapsed === "boolean") {
        setCollapsed(parsed.collapsed);
      }
    } catch {
      // 잘못된 저장값은 기본 비율로 복원한다.
    }
  }, [maxRatio, minRatio, storageKey]);

  useEffect(() => {
    return () => {
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
      }
    };
  }, []);

  const toggleCollapse = useCallback(
    (nextCollapsed: boolean) => {
      setCollapsed(nextCollapsed);
      persistState({ ratio, collapsed: nextCollapsed });
    },
    [persistState, ratio]
  );

  useEffect(() => {
    function handleBracketShortcut(event: globalThis.KeyboardEvent) {
      if (event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      if (isEditableElement(event.target)) {
        return;
      }
      if (typeof document !== "undefined" && document.querySelector("dialog[open]")) {
        return;
      }
      if (event.key === "[") {
        event.preventDefault();
        toggleCollapse(true);
      } else if (event.key === "]") {
        event.preventDefault();
        toggleCollapse(false);
      }
    }

    window.addEventListener("keydown", handleBracketShortcut);
    return () => window.removeEventListener("keydown", handleBracketShortcut);
  }, [toggleCollapse]);

  function handleSeparatorPointerDown(event: React.PointerEvent<HTMLDivElement>) {
    if (event.button !== 0 || collapsed) {
      return;
    }
    const container = containerRef.current;
    if (!container) {
      return;
    }
    const rect = container.getBoundingClientRect();
    if (rect.width <= 0) {
      return;
    }

    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);

    const handleMove = (moveEvent: PointerEvent) => {
      const rawPct = ((moveEvent.clientX - rect.left) / rect.width) * 100;
      pendingRatioRef.current = clampRatio(rawPct, minRatio, maxRatio);
      if (rafRef.current === null) {
        rafRef.current = requestAnimationFrame(() => {
          rafRef.current = null;
          if (pendingRatioRef.current !== null) {
            setRatio(pendingRatioRef.current);
          }
        });
      }
    };

    const handleEnd = () => {
      if (rafRef.current !== null) {
        cancelAnimationFrame(rafRef.current);
        rafRef.current = null;
      }
      const finalRatio = pendingRatioRef.current ?? ratio;
      pendingRatioRef.current = null;
      setRatio(finalRatio);
      setDragging(false);
      persistState({ ratio: finalRatio, collapsed: false });
      window.removeEventListener("pointermove", handleMove);
      window.removeEventListener("pointerup", handleEnd);
      window.removeEventListener("pointercancel", handleEnd);
    };

    window.addEventListener("pointermove", handleMove);
    window.addEventListener("pointerup", handleEnd);
    window.addEventListener("pointercancel", handleEnd);
  }

  function handleSeparatorKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.shiftKey ? 5 : 2;
    if (event.key === "ArrowLeft") {
      event.preventDefault();
      const next = clampRatio(ratio - step, minRatio, maxRatio);
      setCollapsed(false);
      setRatio(next);
      persistState({ ratio: next, collapsed: false });
    } else if (event.key === "ArrowRight") {
      event.preventDefault();
      const next = clampRatio(ratio + step, minRatio, maxRatio);
      setCollapsed(false);
      setRatio(next);
      persistState({ ratio: next, collapsed: false });
    } else if (event.key === "Home") {
      event.preventDefault();
      setCollapsed(false);
      setRatio(minRatio);
      persistState({ ratio: minRatio, collapsed: false });
    } else if (event.key === "End") {
      event.preventDefault();
      setCollapsed(false);
      setRatio(maxRatio);
      persistState({ ratio: maxRatio, collapsed: false });
    }
  }

  const effectiveRatio = collapsed ? 0 : ratio;

  return (
    <div
      ref={containerRef}
      className={cn("flex flex-col gap-6 lg:flex-row lg:items-start lg:gap-0", className)}
    >
      {collapsed ? (
        <div className="hidden shrink-0 lg:flex lg:flex-col lg:items-center lg:pr-2">
          <button
            aria-label={`${leftLabel} 펼치기`}
            className="focus-ring inline-flex items-center gap-1 rounded-md border border-[#d1d9e0] bg-[#f6f8fa] px-2 py-1.5 font-mono text-xs font-medium text-[#1f2328] transition-colors hover:border-[#0969da]/40 hover:bg-[#ddf4ff] hover:text-[#0969da]"
            onClick={() => toggleCollapse(false)}
            title={`${leftLabel} 펼치기 (])`}
            type="button"
          >
            <PanelLeftOpen className="h-3.5 w-3.5" />
            <span>]</span>
          </button>
        </div>
      ) : (
        <div
          className="w-full shrink-0 lg:w-[var(--studio-split-left)]"
          style={{ "--studio-split-left": `${ratio}%` } as React.CSSProperties}
        >
          {leftPane}
        </div>
      )}

      <div className="hidden lg:flex lg:shrink-0 lg:flex-col lg:items-center lg:self-stretch lg:px-2">
        <button
          aria-label={collapsed ? `${leftLabel} 펼치기` : `${leftLabel} 접기`}
          className="focus-ring mb-2 inline-flex h-6 w-6 items-center justify-center rounded-md border border-[#d1d9e0] bg-[#f6f8fa] text-[#59636e] transition-colors hover:border-[#0969da]/40 hover:bg-[#ddf4ff] hover:text-[#0969da]"
          onClick={() => toggleCollapse(!collapsed)}
          title={collapsed ? `${leftLabel} 펼치기 (])` : `${leftLabel} 접기 ([)`}
          type="button"
        >
          {collapsed ? <PanelLeftOpen className="h-3.5 w-3.5" /> : <PanelLeftClose className="h-3.5 w-3.5" />}
        </button>

        <div
          aria-label={`${leftLabel} 너비 조절`}
          aria-orientation="vertical"
          aria-valuemax={maxRatio}
          aria-valuemin={minRatio}
          aria-valuenow={effectiveRatio}
          className={cn(
            "focus-ring min-h-[220px] w-1.5 flex-1 cursor-col-resize rounded-full border transition-colors",
            dragging
              ? "border-[#0969da] bg-[#ddf4ff]"
              : "border-transparent bg-[#eaeef2] hover:border-[#0969da]/40 hover:bg-[#ddf4ff]"
          )}
          onKeyDown={handleSeparatorKeyDown}
          onPointerDown={handleSeparatorPointerDown}
          role="separator"
          tabIndex={0}
        />
      </div>

      <div className="min-w-0 flex-1">{rightPane}</div>
    </div>
  );
}
