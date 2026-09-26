import type { MetadataRoute } from "next";
import { serviceBrandAssets } from "@/lib/serviceBrandAssets";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "aquila buddy",
    short_name: "aquila buddy",
    description: "GitHub open source contribution assistant for aquila buddy",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#18191D",
    theme_color: "#18191D",
    icons: [
      {
        src: "/favicon.ico",
        sizes: "48x48",
        type: "image/x-icon"
      },
      {
        src: "/apple-touch-icon.png",
        sizes: "180x180",
        type: "image/png"
      },
      {
        src: "/brand-mascot.png",
        sizes: "96x96",
        type: "image/png"
      },
      ...serviceBrandAssets.flatMap((asset) => [
        {
          src: asset.favicon,
          sizes: "48x48",
          type: "image/x-icon"
        },
        {
          src: asset.appleTouchIcon,
          sizes: "180x180",
          type: "image/png"
        },
        {
          src: asset.representativeImage,
          sizes: "96x96",
          type: "image/png"
        }
      ])
    ]
  };
}
