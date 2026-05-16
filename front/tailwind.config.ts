import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/lib/**/*.{js,ts,jsx,tsx,mdx}"
  ],
  theme: {
    extend: {
      colors: {
        github: {
          bg: "#101214",
          surface: "#171a1d",
          border: "#3b3a32",
          text: "#efe7d3",
          muted: "#a8a195",
          accent: "#c2a35b",
          purple: "#c2a35b",
          "purple-light": "#d2b46d"
        },
        ink: "#efe7d3",
        paper: "#101214",
        line: "#3b3a32",
        moss: "#8aa0a6",
        amber: "#c2a35b",
        plum: "#d2b46d"
      }
    }
  },
  plugins: []
};

export default config;
