import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "OMOS",
  description: "GitHub open source contribution assistant"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
