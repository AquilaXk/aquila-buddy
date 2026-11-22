const MAX_REASON_PARSE_CHARS = 4096;

export type ParsedIssueReasonMetadata = {
  cleanReason: string;
  starsLabel: string | null;
  starsCount: number | null;
  recentCommitLabel: string | null;
  licenseLabel: string | null;
};

function parseCompactStarsCount(label: string): number | null {
  const lower = label.trim().toLowerCase();
  if (!lower) {
    return null;
  }
  if (lower.endsWith("k")) {
    const base = Number.parseFloat(lower.slice(0, -1));
    return Number.isFinite(base) && base >= 0 ? Math.round(base * 1000) : null;
  }
  const num = Number.parseInt(lower.replaceAll(",", ""), 10);
  return Number.isFinite(num) && num >= 0 ? num : null;
}

/**
 * Parses bounded `[스타수: ..., 최근 커밋: ..., 라이선스: ...]` metadata suffixes emitted by backend issue search
 * in O(n) single-pass index lookups without regex backtracking.
 */
export function parseIssueReasonMetadata(reason?: string | null): ParsedIssueReasonMetadata {
  if (!reason) {
    return {
      cleanReason: "",
      starsLabel: null,
      starsCount: null,
      recentCommitLabel: null,
      licenseLabel: null,
    };
  }

  const bounded = reason.length > MAX_REASON_PARSE_CHARS
    ? reason.slice(0, MAX_REASON_PARSE_CHARS)
    : reason;
  const trimmed = bounded.trim();
  const markerStart = trimmed.lastIndexOf("[스타수:");
  if (markerStart === -1) {
    return {
      cleanReason: trimmed,
      starsLabel: null,
      starsCount: null,
      recentCommitLabel: null,
      licenseLabel: null,
    };
  }

  const markerEnd = trimmed.indexOf("]", markerStart);
  if (markerEnd === -1) {
    return {
      cleanReason: trimmed,
      starsLabel: null,
      starsCount: null,
      recentCommitLabel: null,
      licenseLabel: null,
    };
  }

  const metadataBlock = trimmed.slice(markerStart + 1, markerEnd);
  let starsLabel: string | null = null;
  let starsCount: number | null = null;
  let recentCommitLabel: string | null = null;
  let licenseLabel: string | null = null;

  const segments = metadataBlock.split(",");
  for (let i = 0; i < segments.length; i += 1) {
    const part = segments[i].trim();
    if (part.startsWith("스타수:")) {
      const value = part.slice("스타수:".length).trim();
      if (value) {
        starsLabel = value;
        starsCount = parseCompactStarsCount(value);
      }
    } else if (part.startsWith("최근 커밋:")) {
      const value = part.slice("최근 커밋:".length).trim();
      if (value) {
        recentCommitLabel = value;
      }
    } else if (part.startsWith("라이선스:")) {
      const value = part.slice("라이선스:".length).trim();
      if (value && value.toUpperCase() !== "NOASSERTION") {
        licenseLabel = value;
      }
    }
  }

  const before = trimmed.slice(0, markerStart).trimEnd();
  const after = trimmed.slice(markerEnd + 1).trimStart();
  const cleanReason = after ? `${before} ${after}`.trim() : before;

  return {
    cleanReason: cleanReason || trimmed,
    starsLabel,
    starsCount,
    recentCommitLabel,
    licenseLabel,
  };
}
