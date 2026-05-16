import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "aquila buddy",
  description: "GitHub open source contribution assistant for aquila buddy",
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png"
  }
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
