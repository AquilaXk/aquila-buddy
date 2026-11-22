import type { ReactNode } from "react";

export function generateStaticParams() {
  return [{ id: "_" }];
}

export default function PrDetailLayout({ children }: { children: ReactNode }) {
  return children;
}
