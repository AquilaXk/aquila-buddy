export const MAX_INLINE_DIFF_LINE_LENGTH = 512;

export type InlineDiffSegment = {
  prefix: string;
  changed: string;
  suffix: string;
};

export type InlineDiffResult = {
  whitespaceOnly: boolean;
  oldSegments: InlineDiffSegment;
  newSegments: InlineDiffSegment;
};

function isHighSurrogate(code: number): boolean {
  return code >= 0xd800 && code <= 0xdbff;
}

function isLowSurrogate(code: number): boolean {
  return code >= 0xdc00 && code <= 0xdfff;
}

export function collapseInlineWhitespace(input: string): string {
  let out = "";
  let inSpace = true;
  for (let i = 0; i < input.length; i += 1) {
    const ch = input[i];
    const isSpace = ch === " " || ch === "\t" || ch === "\r" || ch === "\n";
    if (isSpace) {
      if (!inSpace) {
        out += " ";
        inSpace = true;
      }
    } else {
      out += ch;
      inSpace = false;
    }
  }
  return out.endsWith(" ") ? out.slice(0, -1) : out;
}

/**
 * Computes single-pass O(n) common prefix and suffix boundaries between paired diff lines
 * bounded at MAX_INLINE_DIFF_LINE_LENGTH without breaking UTF-16 surrogate pairs.
 */
export function computeInlineDiffSegments(
  oldLine: string,
  newLine: string,
  ignoreWhitespace = false
): InlineDiffResult | null {
  if (
    oldLine.length > MAX_INLINE_DIFF_LINE_LENGTH ||
    newLine.length > MAX_INLINE_DIFF_LINE_LENGTH ||
    oldLine === newLine
  ) {
    return null;
  }

  if (ignoreWhitespace && collapseInlineWhitespace(oldLine) === collapseInlineWhitespace(newLine)) {
    return {
      whitespaceOnly: true,
      oldSegments: { prefix: oldLine, changed: "", suffix: "" },
      newSegments: { prefix: newLine, changed: "", suffix: "" },
    };
  }

  const minLen = Math.min(oldLine.length, newLine.length);
  let prefixLen = 0;
  while (prefixLen < minLen && oldLine.charCodeAt(prefixLen) === newLine.charCodeAt(prefixLen)) {
    prefixLen += 1;
  }
  if (prefixLen > 0 && isHighSurrogate(oldLine.charCodeAt(prefixLen - 1))) {
    prefixLen -= 1;
  }

  let suffixLen = 0;
  const maxSuffix = minLen - prefixLen;
  while (
    suffixLen < maxSuffix &&
    oldLine.charCodeAt(oldLine.length - 1 - suffixLen) ===
      newLine.charCodeAt(newLine.length - 1 - suffixLen)
  ) {
    suffixLen += 1;
  }
  if (
    suffixLen > 0 &&
    isLowSurrogate(oldLine.charCodeAt(oldLine.length - suffixLen))
  ) {
    suffixLen -= 1;
  }

  const oldSuffixStart = oldLine.length - suffixLen;
  const newSuffixStart = newLine.length - suffixLen;

  return {
    whitespaceOnly: false,
    oldSegments: {
      prefix: oldLine.slice(0, prefixLen),
      changed: oldLine.slice(prefixLen, oldSuffixStart),
      suffix: oldLine.slice(oldSuffixStart),
    },
    newSegments: {
      prefix: newLine.slice(0, prefixLen),
      changed: newLine.slice(prefixLen, newSuffixStart),
      suffix: newLine.slice(newSuffixStart),
    },
  };
}
