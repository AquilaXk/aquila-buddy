import type { Config } from "tailwindcss";

const config: Config = {
  darkMode: "class",
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
          50: "#F8F9FE",
          100: "#F0F2FD",
          200: "#E2E5FD",
          300: "#CCD2FC",
          400: "#8E9CE6",
          500: "#5C6BC0", // Signature Lavender
          600: "#4D5CB5", // Hover
          700: "#3F4EA3", // Active
          800: "#333F85",
          900: "#262F64",
          950: "#171C3D",
        },
        primary: {
          DEFAULT: "#5C6BC0",
          hover: "#4D5CB5",
          50: "#F8F9FE",
          100: "#F0F2FD",
          200: "#E2E5FD",
          300: "#CCD2FC",
          400: "#8E9CE6",
          500: "#5C6BC0",
          600: "#4D5CB5",
          700: "#3F4EA3",
        },
        neutral: {
          white: "#FFFFFF",
          surface: "#FFFFFF",
          scaffold: "#F7F8FC",
          subtle: "#F0F2F7",
          border: "#E1E4EE",
        },
        ink: {
          primary: "#18191D",
          secondary: "#4D536B",
          muted: "#697089",
        },
        status: {
          success: "#0A705A",
          warning: "#9A5600",
          danger: "#D9383A",
          info: "#215EA8",
          successSoft: "#F0FBF7",
          warningSoft: "#FFF0D1",
          dangerSoft: "#FEE2E2",
          infoSoft: "#EEF5FF",
        },
        dark: {
          scaffold: "#18191D", // Dark Neutral Charcoal Scaffold
          surface: "#212529",  // Elevated Card
          elevated: "#2A2E33",
          border: "#343A40",
          text: "#F8F9FA",
          muted: "#9CA3AF",
        },
        github: {
          bg: "#18191D",
          surface: "#212529",
          border: "#343A40",
          text: "#F8F9FA",
          muted: "#9CA3AF",
          accent: "#5C6BC0",
          purple: "#5C6BC0",
          "purple-light": "#8E9CE6",
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
