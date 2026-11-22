import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/utils";

export type ButtonVariant =
  | "default"
  | "primerGreen"
  | "outline"
  | "secondary"
  | "ghost"
  | "destructive"
  | "link";

export type ButtonSize = "default" | "sm" | "xs" | "lg" | "icon";

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
}

const BUTTON_BASE_CLASS =
  "focus-ring inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-[6px] font-medium transition-colors disabled:pointer-events-none disabled:opacity-50";

const BUTTON_VARIANT_CLASSES: Record<ButtonVariant, string> = {
  default: "border border-[#1f2328] bg-[#1f2328] text-white shadow-xs hover:border-[#32383f] hover:bg-[#32383f]",
  primerGreen: "border border-[#1f2328]/15 bg-[#1f883d] text-white shadow-xs hover:bg-[#1c8139]",
  outline: "border border-[#d1d9e0] bg-[#ffffff] text-[#1f2328] shadow-xs hover:bg-[#f6f8fa]",
  secondary:
    "border border-[#d1d9e0] bg-[#f6f8fa] text-[#1f2328] shadow-xs hover:border-[#b9c3ce] hover:bg-[#eff2f5]",
  ghost: "border border-transparent bg-transparent text-[#59636e] hover:bg-[#eaeef2]/70 hover:text-[#1f2328]",
  destructive: "border border-[#d1242f] bg-[#d1242f] text-white shadow-xs hover:bg-[#a40e26]",
  link: "border border-transparent bg-transparent text-[#0969da] underline-offset-4 hover:underline",
};

const BUTTON_SIZE_CLASSES: Record<ButtonSize, string> = {
  default: "h-8 px-3 py-1.5 text-xs",
  sm: "h-7 px-2.5 py-1 text-xs",
  xs: "h-6 px-2 py-0.5 text-[11px]",
  lg: "h-9 px-4 py-2 text-[13px]",
  icon: "h-8 w-8 p-0 text-xs",
};

export function Button({
  className,
  variant = "default",
  size = "default",
  type = "button",
  ...props
}: ButtonProps) {
  return (
    <button
      className={cn(
        BUTTON_BASE_CLASS,
        BUTTON_VARIANT_CLASSES[variant],
        BUTTON_SIZE_CLASSES[size],
        className
      )}
      type={type}
      {...props}
    />
  );
}
