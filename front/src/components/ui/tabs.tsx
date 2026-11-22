"use client";

import {
  createContext,
  useCallback,
  useContext,
  useId,
  useState,
  type ButtonHTMLAttributes,
  type HTMLAttributes,
  type KeyboardEvent,
} from "react";
import { cn } from "@/lib/utils";

type TabsContextValue = {
  baseId: string;
  selectedValue: string;
  selectValue: (value: string) => void;
};

const TabsContext = createContext<TabsContextValue | null>(null);

function useTabsContext(componentName: string): TabsContextValue {
  const ctx = useContext(TabsContext);
  if (!ctx) {
    throw new Error(`${componentName} must be rendered inside <Tabs>.`);
  }
  return ctx;
}

export interface TabsProps extends HTMLAttributes<HTMLDivElement> {
  value?: string;
  defaultValue?: string;
  onValueChange?: (value: string) => void;
}

export function Tabs({
  value,
  defaultValue = "",
  onValueChange,
  className,
  children,
  ...props
}: TabsProps) {
  const baseId = useId();
  const [uncontrolledValue, setUncontrolledValue] = useState(defaultValue);
  const isControlled = value !== undefined;
  const selectedValue = isControlled ? value : uncontrolledValue;

  const selectValue = useCallback(
    (nextValue: string) => {
      if (!isControlled) {
        setUncontrolledValue(nextValue);
      }
      onValueChange?.(nextValue);
    },
    [isControlled, onValueChange]
  );

  return (
    <TabsContext.Provider value={{ baseId, selectedValue, selectValue }}>
      <div className={cn("flex flex-col gap-2", className)} {...props}>
        {children}
      </div>
    </TabsContext.Provider>
  );
}

export function TabsList({ className, onKeyDown, ...props }: HTMLAttributes<HTMLDivElement>) {
  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    onKeyDown?.(event);
    if (event.defaultPrevented) {
      return;
    }

    const key = event.key;
    if (key !== "ArrowLeft" && key !== "ArrowRight" && key !== "Home" && key !== "End") {
      return;
    }

    const tabButtons = Array.from(
      event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]:not(:disabled)')
    );
    if (tabButtons.length === 0) {
      return;
    }

    const currentIndex = tabButtons.indexOf(event.target as HTMLButtonElement);
    if (currentIndex === -1) {
      return;
    }

    let nextIndex = currentIndex;
    if (key === "ArrowRight") {
      nextIndex = (currentIndex + 1) % tabButtons.length;
    } else if (key === "ArrowLeft") {
      nextIndex = (currentIndex - 1 + tabButtons.length) % tabButtons.length;
    } else if (key === "Home") {
      nextIndex = 0;
    } else if (key === "End") {
      nextIndex = tabButtons.length - 1;
    }

    event.preventDefault();
    const targetButton = tabButtons[nextIndex];
    targetButton.focus();
    targetButton.click();
  }

  return (
    <div
      aria-orientation="horizontal"
      className={cn("inline-flex items-center gap-0.5 rounded-md border border-[#d1d9e0] bg-[#eaeef2] p-0.5", className)}
      onKeyDown={handleKeyDown}
      role="tablist"
      {...props}
    />
  );
}

export interface TabsTriggerProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  value: string;
}

export function TabsTrigger({ value, className, onClick, ...props }: TabsTriggerProps) {
  const { baseId, selectedValue, selectValue } = useTabsContext("TabsTrigger");
  const isSelected = selectedValue === value;

  return (
    <button
      aria-controls={`${baseId}-panel-${value}`}
      aria-selected={isSelected}
      className={cn(
        "focus-ring inline-flex items-center justify-center whitespace-nowrap rounded-[5px] border px-2.5 py-1 text-xs transition-colors disabled:pointer-events-none disabled:opacity-50",
        isSelected
          ? "border-[#d1d9e0] bg-[#ffffff] font-semibold text-[#1f2328] shadow-xs"
          : "border-transparent font-medium text-[#59636e] hover:text-[#1f2328]",
        className
      )}
      data-state={isSelected ? "active" : "inactive"}
      id={`${baseId}-tab-${value}`}
      onClick={(event) => {
        onClick?.(event);
        if (!event.defaultPrevented) {
          selectValue(value);
        }
      }}
      role="tab"
      tabIndex={isSelected ? 0 : -1}
      type="button"
      {...props}
    />
  );
}

export interface TabsContentProps extends HTMLAttributes<HTMLDivElement> {
  value: string;
}

export function TabsContent({ value, className, ...props }: TabsContentProps) {
  const { baseId, selectedValue } = useTabsContext("TabsContent");
  const isSelected = selectedValue === value;

  return (
    <div
      aria-labelledby={`${baseId}-tab-${value}`}
      className={cn("focus-ring outline-none", className)}
      data-state={isSelected ? "active" : "inactive"}
      hidden={!isSelected}
      id={`${baseId}-panel-${value}`}
      role="tabpanel"
      tabIndex={0}
      {...props}
    />
  );
}
