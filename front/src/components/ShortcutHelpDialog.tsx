"use client";

import { useCallback, useEffect, useId, useRef, useState } from "react";
import { Keyboard, X } from "./icons";
import { Kbd, KbdGroup } from "./ui/kbd";

export const OPEN_SHORTCUT_HELP_EVENT = "aquila-buddy-open-shortcuts";

const SHORTCUT_ITEMS: Array<{ keys: string[]; description: string }> = [
  { keys: ["⌘K", "Ctrl+K"], description: "빠른 명령·탐색 열기" },
  { keys: ["?"], description: "키보드 단축키 도움말 열기/닫기" },
  { keys: ["/"], description: "추천 이슈 검색 입력으로 이동" },
  { keys: ["j", "k"], description: "추천 이슈 목록 위/아래 항목 이동" },
  { keys: ["Enter", "o"], description: "선택한 추천 이슈 상세 열기" },
  { keys: ["b"], description: "선택한 추천 이슈 북마크 전환" },
  { keys: ["[", "]"], description: "상세 화면 좌측 패널 접기/펼치기" },
  { keys: ["Esc"], description: "열린 대화상자 닫기" },
];

function isEditableTarget(target: EventTarget | null): boolean {
  const element = target as HTMLElement | null;
  if (!element) {
    return false;
  }
  return (
    element.tagName === "INPUT" ||
    element.tagName === "TEXTAREA" ||
    element.tagName === "SELECT" ||
    element.isContentEditable
  );
}

export default function ShortcutHelpDialog() {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const headingId = useId();
  const [open, setOpen] = useState(false);

  const openDialog = useCallback(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }
    setOpen(true);
    if (!dialog.open) {
      dialog.showModal();
    }
    closeBtnRef.current?.focus();
  }, []);

  const closeDialog = useCallback(() => {
    const dialog = dialogRef.current;
    if (dialog?.open) {
      dialog.close();
    }
    setOpen(false);
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== "?" || event.metaKey || event.ctrlKey || event.altKey) {
        return;
      }
      if (isEditableTarget(event.target)) {
        return;
      }
      const activeDialog = document.querySelector("dialog[open]");
      if (activeDialog && activeDialog !== dialogRef.current) {
        return;
      }
      event.preventDefault();
      if (dialogRef.current?.open) {
        closeDialog();
      } else {
        openDialog();
      }
    }

    function handleOpenEvent() {
      openDialog();
    }

    window.addEventListener("keydown", handleKeyDown);
    window.addEventListener(OPEN_SHORTCUT_HELP_EVENT, handleOpenEvent);
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener(OPEN_SHORTCUT_HELP_EVENT, handleOpenEvent);
    };
  }, [closeDialog, openDialog]);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) {
      return;
    }

    function handleClose() {
      if (dialog) {
        dialog.returnValue = "";
      }
      setOpen(false);
    }

    dialog.addEventListener("close", handleClose);

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
          closeDialog();
        }
        pressedOutside = false;
      };
      dialog.addEventListener("pointerdown", onPointerDown);
      dialog.addEventListener("pointerup", onPointerUp);
      return () => {
        dialog.removeEventListener("close", handleClose);
        dialog.removeEventListener("pointerdown", onPointerDown);
        dialog.removeEventListener("pointerup", onPointerUp);
      };
    }

    return () => {
      dialog.removeEventListener("close", handleClose);
    };
  }, [closeDialog]);

  return (
    <dialog
      aria-labelledby={headingId}
      className="studio-command-dialog"
      data-open={open ? "true" : "false"}
      ref={dialogRef}
      {...({ closedby: "any" } as Record<string, unknown>)}
    >
      <div className="overflow-hidden rounded-xl border border-[#d1d9e0] bg-white shadow-xl">
        <div className="primer-box-header flex items-center justify-between px-4 py-2.5">
          <div className="flex items-center gap-2">
            <Keyboard className="h-4 w-4 text-[#0969da]" />
            <h2 className="text-[13px] font-semibold tracking-[-0.015em] text-[#1f2328]" id={headingId}>
              키보드 단축키
            </h2>
          </div>
          <button
            aria-label="단축키 도움말 닫기"
            autoFocus
            className="focus-ring inline-flex h-6 w-6 items-center justify-center rounded-md text-[#59636e] hover:bg-[#eaeef2] hover:text-[#1f2328]"
            onClick={closeDialog}
            ref={closeBtnRef}
            type="button"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>

        <ul className="divide-y divide-[#d1d9e0]/60">
          {SHORTCUT_ITEMS.map((item) => (
            <li
              className="flex items-center justify-between gap-4 px-4 py-2.5 text-[13px]"
              key={item.description}
            >
              <span className="text-[#1f2328]">{item.description}</span>
              <KbdGroup className="shrink-0">
                {item.keys.map((keyLabel) => (
                  <Kbd key={keyLabel}>{keyLabel}</Kbd>
                ))}
              </KbdGroup>
            </li>
          ))}
        </ul>

        <div className="flex items-center justify-between border-t border-[#d1d9e0] bg-[#f6f8fa] px-4 py-2 text-[11px] text-[#59636e]">
          <span>입력창에 포커스가 있을 때는 비활성화됩니다</span>
          <Kbd>ESC</Kbd>
        </div>
      </div>
    </dialog>
  );
}
