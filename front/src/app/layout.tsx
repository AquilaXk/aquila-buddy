import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  applicationName: "Aquila Buddy",
  metadataBase: new URL("https://buddy.aquilaxk.site"),
  title: "Aquila Buddy",
  description: "GitHub 오픈소스 이슈 추천, 코드 맥락 분석 및 PR 초안 작성을 지원하는 기여 워크스페이스",
  openGraph: {
    title: "Aquila Buddy",
    description: "GitHub 오픈소스 이슈 추천, 코드 맥락 분석 및 PR 초안 작성을 지원하는 기여 워크스페이스",
    siteName: "Aquila Buddy",
    type: "website",
    images: [
      {
        url: "/icon.svg",
        width: 64,
        height: 64,
        alt: "Aquila Buddy"
      }
    ]
  },
  icons: {
    icon: [
      { url: "/icon.svg", type: "image/svg+xml" },
      { url: "/favicon.ico" }
    ],
    apple: "/apple-touch-icon.png"
  }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#ffffff",
  colorScheme: "light"
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
