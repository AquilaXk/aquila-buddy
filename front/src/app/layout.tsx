import type { Metadata, Viewport } from "next";
import { serviceBrandAssets } from "@/lib/serviceBrandAssets";
import "./globals.css";

export const metadata: Metadata = {
  applicationName: "aquila buddy",
  metadataBase: new URL("https://aquila-buddy.aquilaxk.site"),
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
      },
      ...serviceBrandAssets.map((asset) => ({
        url: asset.representativeImage,
        width: 96,
        height: 96,
        alt: asset.name
      }))
    ]
  },
  icons: {
    icon: "/favicon.ico",
    apple: "/apple-touch-icon.png",
    other: serviceBrandAssets.flatMap((asset) => [
      {
        rel: "alternate icon",
        url: asset.favicon
      },
      {
        rel: "apple-touch-icon",
        url: asset.appleTouchIcon
      }
    ])
  }
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#18191D",
  colorScheme: "dark"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className="dark">
      <body>{children}</body>
    </html>
  );
}
