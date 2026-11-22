import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      maxWidth: {
        "8xl": "90rem",
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
      boxShadow: {
        xs: "0 1px 1px 0 rgba(31, 35, 40, 0.04)",
      },
      colors: {
        background: "var(--background)",
        foreground: "var(--foreground)",
        card: {
          DEFAULT: "var(--card)",
          foreground: "var(--card-foreground)",
        },
        popover: {
          DEFAULT: "var(--popover)",
          foreground: "var(--popover-foreground)",
        },
        primary: {
          DEFAULT: "#1f2328",
          foreground: "#ffffff",
          hover: "#32383f",
        },
        secondary: {
          DEFAULT: "#f6f8fa",
          foreground: "#1f2328",
        },
        muted: {
          DEFAULT: "#f6f8fa",
          foreground: "#59636e",
        },
        accent: {
          DEFAULT: "#0969da",
          foreground: "#ffffff",
          muted: "#ddf4ff",
        },
        destructive: {
          DEFAULT: "#d1242f",
          foreground: "#ffffff",
        },
        border: "#d1d9e0",
        input: "#d1d9e0",
        ring: "#0969da",
        brand: {
          50: "#ddf4ff",
          100: "#b6e3ff",
          500: "#0969da",
          600: "#0550ae",
          950: "#1f2328",
        },
        neutral: {
          white: "#ffffff",
          surface: "#ffffff",
          scaffold: "#ffffff",
          subtle: "#f6f8fa",
          hover: "#eff2f5",
          border: "#d1d9e0",
          borderStrong: "#818b98",
        },
        ink: {
          primary: "#1f2328",
          secondary: "#59636e",
          muted: "#59636e",
        },
        status: {
          success: "#1a7f37",
          successEmphasis: "#1f883d",
          warning: "#9a6700",
          danger: "#d1242f",
          info: "#0969da",
          done: "#8250df",
          successSoft: "#dafbe1",
          warningSoft: "#fff8c5",
          dangerSoft: "#ffebe9",
          infoSoft: "#ddf4ff",
          doneSoft: "#f1e5ff",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)"],
        mono: ["var(--font-mono)"],
      },
    },
  },
  plugins: [],
};

export default config;
