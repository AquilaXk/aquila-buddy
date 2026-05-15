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
        ink: "#161513",
        paper: "#fbfaf7",
        line: "#d8d2c8",
        moss: "#2f6b4f",
        amber: "#b96b2c",
        plum: "#6d4b70"
      }
    }
  },
  plugins: []
};

export default config;
