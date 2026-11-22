import type { ReactNode } from "react";

export function generateStaticParams() {
  return [{ id: "_" }];
}

export default function IssueDetailLayout({ children }: { children: ReactNode }) {
  return children;
}
