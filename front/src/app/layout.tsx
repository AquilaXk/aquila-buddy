import type { Metadata } from "next";
import "./globals.css";
import { Navbar } from "@/components/Navbar";

export const metadata: Metadata = {
  title: "Aquila Buddy",
  description: "GitHub open source contribution assistant"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>
        <Navbar />
        <main className="mx-auto min-h-screen max-w-6xl px-5 py-6">{children}</main>
      </body>
    </html>
  );
}
