import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  applicationName: "aquila buddy",
  title: "aquila buddy",
  description: "GitHub open source contribution assistant for aquila buddy",
  openGraph: {
    title: "aquila buddy",
    description: "GitHub open source contribution assistant for aquila buddy",
    siteName: "aquila buddy",
    type: "website",
    images: [
      {
        url: "/brand-mascot.png",
        width: 96,
        height: 96,
        alt: "aquila buddy"
      }
    ]
  },
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png",
    other: [
      {
        rel: "alternate icon",
        url: "/blog-favicon.ico"
      }
    ]
  }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#121416",
  colorScheme: "dark"
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
