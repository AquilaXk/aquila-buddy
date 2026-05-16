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
          bg: "#0d1117",
          surface: "#161b22",
          border: "#30363d",
          text: "#c9d1d9",
          muted: "#8b949e",
          accent: "#58a6ff",
          purple: "#7c3aed",
          "purple-light": "#a78bfa"
        },
        ink: "#c9d1d9",
        paper: "#0d1117",
        line: "#30363d",
        moss: "#58a6ff",
        amber: "#f2cc60",
        plum: "#a78bfa"
      }
    }
  },
  plugins: []
};

export default config;
