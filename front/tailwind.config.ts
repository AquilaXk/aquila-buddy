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
      colors: {
        brand: {
          50: "#eef4fe",
          100: "#ddeafe",
          200: "#bfd8fd",
          300: "#93befa",
          400: "#589bf4",
          500: "#1a73e8",
          600: "#1557b0",
          700: "#174ea6",
          800: "#184188",
          900: "#19366d",
          950: "#121317",
        },
        primary: {
          DEFAULT: "#121317",
          hover: "#2f3034",
          50: "#eef4fe",
          100: "#ddeafe",
          200: "#bfd8fd",
          300: "#93befa",
          400: "#589bf4",
          500: "#1a73e8",
          600: "#1557b0",
          700: "#174ea6",
        },
        neutral: {
          white: "#ffffff",
          surface: "#ffffff",
          scaffold: "#ffffff",
          subtle: "#f8f9fc",
          hover: "#eff2f7",
          border: "#e2e5ec",
          borderStrong: "#cdd4dc",
        },
        ink: {
          primary: "#121317",
          secondary: "#45474d",
          muted: "#6a6a71",
        },
        status: {
          success: "#137333",
          warning: "#b06000",
          danger: "#c5221f",
          info: "#1a73e8",
          successSoft: "#e6f4ea",
          warningSoft: "#fef7e0",
          dangerSoft: "#fce8e6",
          infoSoft: "#eef4fe",
        },
        dark: {
          scaffold: "#ffffff",
          surface: "#ffffff",
          elevated: "#f8f9fc",
          border: "#e2e5ec",
          text: "#121317",
          muted: "#45474d",
        },
      },
      fontFamily: {
        sans: [
          "-apple-system",
          "BlinkMacSystemFont",
          '"Segoe UI"',
          "Roboto",
          '"Helvetica Neue"',
          "Arial",
          "sans-serif",
        ],
      },
    },
  },
  plugins: [],
};

export default config;
