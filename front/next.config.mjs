const isDesktopExport =
  process.env.BUILD_TARGET === "desktop" || Boolean(process.env.TAURI_ENV_PLATFORM);

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  ...(isDesktopExport
    ? {
        output: "export",
        trailingSlash: true,
        images: {
          unoptimized: true
        }
      }
    : {})
};

export default nextConfig;
