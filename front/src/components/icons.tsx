import type { ReactNode, SVGProps } from "react";

export type IconProps = SVGProps<SVGSVGElement>;

function createIcon(displayName: string, body: ReactNode) {
  function GeometricIcon(props: IconProps) {
    return (
      <svg
        aria-hidden={props["aria-label"] ? undefined : true}
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth={1.8}
        viewBox="0 0 24 24"
        {...props}
      >
        {body}
      </svg>
    );
  }
  GeometricIcon.displayName = displayName;
  return GeometricIcon;
}

export const AlertCircle = createIcon(
  "AlertCircle",
  <>
    <circle cx="12" cy="12" r="9.5" />
    <path d="M12 8v4.5M12 16h.01" />
  </>
);

export const AlertTriangle = createIcon(
  "AlertTriangle",
  <>
    <path d="M10.3 3.9 2.3 17.7a1.9 1.9 0 0 0 1.65 2.85h16.1a1.9 1.9 0 0 0 1.65-2.85L13.7 3.9a1.9 1.9 0 0 0-3.4 0Z" />
    <path d="M12 9.5v4M12 17h.01" />
  </>
);

export const ArrowLeft = createIcon("ArrowLeft", <path d="M19 12H5M11 6l-6 6 6 6" />);

export const ArrowUpRight = createIcon("ArrowUpRight", <path d="M7 17 17 7M8 7h9v9" />);

export const Bookmark = createIcon(
  "Bookmark",
  <path d="M6 4.5A1.5 1.5 0 0 1 7.5 3h9A1.5 1.5 0 0 1 18 4.5V21l-6-3.8L6 21V4.5Z" />
);

export const Check = createIcon("Check", <path d="M5 12.5 9.5 17 19 7" />);

export const CheckCircle2 = createIcon(
  "CheckCircle2",
  <>
    <circle cx="12" cy="12" r="9.5" />
    <path d="m8.5 12.5 2.3 2.3 4.7-5.1" />
  </>
);

export const ChevronDown = createIcon("ChevronDown", <path d="m6 9 6 6 6-6" />);

export const ChevronRight = createIcon("ChevronRight", <path d="m9 6 6 6-6 6" />);

export const CircleDot = createIcon(
  "CircleDot",
  <>
    <circle cx="12" cy="12" r="9.5" />
    <circle cx="12" cy="12" fill="currentColor" r="1.2" />
  </>
);

export const Code2 = createIcon("Code2", <path d="m8 8-4 4 4 4M16 8l4 4-4 4M13.5 5.5l-3 13" />);

export const Columns2 = createIcon(
  "Columns2",
  <>
    <rect height="16" rx="2" width="18" x="3" y="4" />
    <path d="M12 4v16" />
  </>
);

export const Copy = createIcon(
  "Copy",
  <>
    <rect height="11" rx="2" width="11" x="9" y="9" />
    <path d="M5 15H4a1.5 1.5 0 0 1-1.5-1.5V4.5A1.5 1.5 0 0 1 4 3h9a1.5 1.5 0 0 1 1.5 1.5V5.5" />
  </>
);

export const Edit2 = createIcon(
  "Edit2",
  <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
);

export const ExternalLink = createIcon(
  "ExternalLink",
  <path d="M15 3h6v6M10 14 21 3M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
);

export const FileCode = createIcon(
  "FileCode",
  <>
    <path d="M14 2.5H6.5a2 2 0 0 0-2 2v15a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V8Z" />
    <path d="M14 2.5V8h5.5M10 13l-2 2 2 2M14 13l2 2-2 2" />
  </>
);

export const FileText = createIcon(
  "FileText",
  <>
    <path d="M14 2.5H6.5a2 2 0 0 0-2 2v15a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2V8Z" />
    <path d="M14 2.5V8h5.5M8.5 13h7M8.5 17h5" />
  </>
);

export const GitCommitHorizontal = createIcon(
  "GitCommitHorizontal",
  <>
    <circle cx="12" cy="12" r="3" />
    <path d="M3 12h6M15 12h6" />
  </>
);

export const GitPullRequest = createIcon(
  "GitPullRequest",
  <>
    <circle cx="6" cy="6" r="2.5" />
    <circle cx="6" cy="18" r="2.5" />
    <circle cx="18" cy="18" r="2.5" />
    <path d="M6 8.5v7M18 15.5V9a3 3 0 0 0-3-3h-2M15 4l-2.5 2L15 8" />
  </>
);

export const Globe = createIcon(
  "Globe",
  <>
    <circle cx="12" cy="12" r="9.5" />
    <path d="M2.5 12h19M12 2.5a13.5 13.5 0 0 1 0 19M12 2.5a13.5 13.5 0 0 0 0 19" />
  </>
);

export const HelpCircle = createIcon(
  "HelpCircle",
  <>
    <circle cx="12" cy="12" r="9.5" />
    <path d="M9.5 9.2a2.6 2.6 0 0 1 4.9.9c0 1.8-2.4 2.4-2.4 3.9M12 17.2h.01" />
  </>
);

export const Home = createIcon(
  "Home",
  <>
    <path d="M3.5 10.5 12 3.5l8.5 7M5.5 9.5V20a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1V9.5M9.5 21v-6h5v6" />
  </>
);

export const Keyboard = createIcon(
  "Keyboard",
  <>
    <rect height="13" rx="2" width="19" x="2.5" y="5.5" />
    <path d="M6.5 9.5h.01M10 9.5h.01M14 9.5h.01M17.5 9.5h.01M6.5 12.5h.01M17.5 12.5h.01M8.5 15.5h7" />
  </>
);

export const LayoutList = createIcon(
  "LayoutList",
  <>
    <rect height="5" rx="1" width="5" x="3" y="4.5" />
    <rect height="5" rx="1" width="5" x="3" y="14.5" />
    <path d="M12 5.5h9M12 9h6M12 15.5h9M12 19h6" />
  </>
);

export const Loader2 = createIcon("Loader2", <path d="M21 12a9 9 0 1 1-6.22-8.56" />);

export const LogOut = createIcon(
  "LogOut",
  <path d="M9 20H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9" />
);

export const Menu = createIcon("Menu", <path d="M4 6.5h16M4 12h16M4 17.5h16" />);

export const PanelLeft = createIcon(
  "PanelLeft",
  <>
    <rect height="16" rx="2" width="18" x="3" y="4" />
    <path d="M9 4v16" />
  </>
);

export const PanelLeftClose = createIcon(
  "PanelLeftClose",
  <>
    <rect height="16" rx="2" width="18" x="3" y="4" />
    <path d="M9 4v16M16 9.5 13.5 12l2.5 2.5" />
  </>
);

export const PanelLeftOpen = createIcon(
  "PanelLeftOpen",
  <>
    <rect height="16" rx="2" width="18" x="3" y="4" />
    <path d="M9 4v16M14 9.5 16.5 12 14 14.5" />
  </>
);

export const Plus = createIcon("Plus", <path d="M12 5v14M5 12h14" />);

export const RefreshCw = createIcon(
  "RefreshCw",
  <>
    <path d="M20 11a8 8 0 0 0-14.2-4.8L4 8M4 3.5V8h4.5M4 13a8 8 0 0 0 14.2 4.8L20 16M20 20.5V16h-4.5" />
  </>
);

export const Rows3 = createIcon(
  "Rows3",
  <>
    <rect height="16" rx="2" width="18" x="3" y="4" />
    <path d="M3 9.3h18M3 14.7h18" />
  </>
);

export const Save = createIcon(
  "Save",
  <>
    <path d="M18.5 20.5h-13a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2h10.5L20.5 8v10.5a2 2 0 0 1-2 2Z" />
    <path d="M16.5 20.5v-6.5h-9v6.5M8 3.5v4.5h7" />
  </>
);

export const Search = createIcon(
  "Search",
  <>
    <circle cx="10.5" cy="10.5" r="6.5" />
    <path d="m15.3 15.3 5.2 5.2" />
  </>
);

export const Square = createIcon("Square", <rect height="14" rx="2" width="14" x="5" y="5" />);

export const Star = createIcon(
  "Star",
  <polygon points="12 2.8 14.85 8.55 21.2 9.48 16.6 13.95 17.68 20.28 12 17.28 6.32 20.28 7.4 13.95 2.8 9.48 9.15 8.55 12 2.8" />
);

export const Trash2 = createIcon(
  "Trash2",
  <>
    <path d="M3.5 6.5h17M9 6.5v-2a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M6 6.5l1 13a1.5 1.5 0 0 0 1.5 1.5h7a1.5 1.5 0 0 0 1.5-1.5l1-13M10 10.5v6M14 10.5v6" />
  </>
);

export const User = createIcon(
  "User",
  <>
    <circle cx="12" cy="8" r="3.5" />
    <path d="M5 20a7 7 0 0 1 14 0" />
  </>
);

export const X = createIcon("X", <path d="M18 6 6 18M6 6l12 12" />);
