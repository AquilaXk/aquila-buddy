import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "aquila buddy",
    short_name: "aquila buddy",
    description: "GitHub open source contribution assistant for aquila buddy",
    start_url: "/",
    scope: "/",
    display: "standalone",
    background_color: "#101214",
    theme_color: "#121416",
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
      }
    ]
  };
}
