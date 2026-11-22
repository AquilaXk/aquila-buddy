import { createElement, type ReactNode } from "react";

const MAX_HIGHLIGHT_LINE_CHARS = 512;

export type CodeTokenKind = "plain" | "keyword" | "string" | "comment" | "number";

export type CodeToken = {
  kind: CodeTokenKind;
  text: string;
};

const CODE_KEYWORDS = new Set([
  "abstract",
  "as",
  "async",
  "await",
  "boolean",
  "break",
  "case",
  "catch",
  "class",
  "const",
  "continue",
  "def",
  "default",
  "delete",
  "do",
  "elif",
  "else",
  "enum",
  "export",
  "extends",
  "false",
  "final",
  "finally",
  "fn",
  "for",
  "from",
  "func",
  "function",
  "if",
  "impl",
  "implements",
  "import",
  "in",
  "instanceof",
  "interface",
  "let",
  "match",
  "new",
  "null",
  "number",
  "package",
  "private",
  "protected",
  "pub",
  "public",
  "readonly",
  "return",
  "static",
  "string",
  "struct",
  "super",
  "switch",
  "this",
  "throw",
  "throws",
  "true",
  "try",
  "type",
  "typeof",
  "undefined",
  "var",
  "void",
  "while",
  "yield",
]);

const TOKEN_CLASS_NAMES: Record<Exclude<CodeTokenKind, "plain">, string> = {
  keyword: "font-medium text-[#cf222e]",
  string: "text-[#0a3069]",
  comment: "italic text-[#59636e]",
  number: "text-[#0550ae]",
};

function isIdentStart(code: number): boolean {
  return (
    (code >= 65 && code <= 90) ||
    (code >= 97 && code <= 122) ||
    code === 95 ||
    code === 36
  );
}

function isIdentPart(code: number): boolean {
  return isIdentStart(code) || (code >= 48 && code <= 57);
}

function isDigit(code: number): boolean {
  return code >= 48 && code <= 57;
}

function isWhitespace(code: number): boolean {
  return code === 32 || code === 9;
}

function pushToken(tokens: CodeToken[], kind: CodeTokenKind, text: string): void {
  if (!text) {
    return;
  }
  const prev = tokens[tokens.length - 1];
  if (kind === "plain" && prev && prev.kind === "plain") {
    prev.text += text;
    return;
  }
  tokens.push({ kind, text });
}

/**
 * 1-pass character scanner for common JS/TS/Java/Python/Go/Rust syntax tokens.
 * Bounds work at MAX_HIGHLIGHT_LINE_CHARS and returns plain spans without HTML injection.
 */
export function tokenizeCodeLine(line: string): CodeToken[] {
  if (!line) {
    return [];
  }

  const limit = Math.min(line.length, MAX_HIGHLIGHT_LINE_CHARS);
  const tokens: CodeToken[] = [];
  let plainStart = 0;
  let i = 0;

  while (i < limit) {
    const ch = line.charCodeAt(i);
    const next = i + 1 < limit ? line.charCodeAt(i + 1) : -1;

    if (ch === 47 && next === 47) {
      pushToken(tokens, "plain", line.slice(plainStart, i));
      pushToken(tokens, "comment", line.slice(i));
      return tokens;
    }

    if (ch === 35 && (i === 0 || isWhitespace(line.charCodeAt(i - 1)))) {
      pushToken(tokens, "plain", line.slice(plainStart, i));
      pushToken(tokens, "comment", line.slice(i));
      return tokens;
    }

    if (ch === 47 && next === 42) {
      pushToken(tokens, "plain", line.slice(plainStart, i));
      const closeIdx = line.indexOf("*/", i + 2);
      const end = closeIdx !== -1 && closeIdx + 2 <= limit ? closeIdx + 2 : limit;
      pushToken(tokens, "comment", line.slice(i, end));
      i = end;
      plainStart = i;
      continue;
    }

    if (ch === 34 || ch === 39 || ch === 96) {
      pushToken(tokens, "plain", line.slice(plainStart, i));
      const quote = ch;
      let j = i + 1;
      while (j < limit) {
        const c = line.charCodeAt(j);
        if (c === 92) {
          j += 2;
          continue;
        }
        if (c === quote) {
          j += 1;
          break;
        }
        j += 1;
      }
      const end = Math.min(j, limit);
      pushToken(tokens, "string", line.slice(i, end));
      i = end;
      plainStart = i;
      continue;
    }

    if (isDigit(ch) && (i === 0 || !isIdentPart(line.charCodeAt(i - 1)))) {
      pushToken(tokens, "plain", line.slice(plainStart, i));
      let j = i + 1;
      while (j < limit) {
        const c = line.charCodeAt(j);
        if (
          isDigit(c) ||
          c === 46 ||
          c === 95 ||
          c === 120 ||
          c === 88 ||
          (c >= 97 && c <= 102) ||
          (c >= 65 && c <= 70)
        ) {
          j += 1;
          continue;
        }
        break;
      }
      pushToken(tokens, "number", line.slice(i, j));
      i = j;
      plainStart = i;
      continue;
    }

    if (isIdentStart(ch)) {
      let j = i + 1;
      while (j < limit && isIdentPart(line.charCodeAt(j))) {
        j += 1;
      }
      const word = line.slice(i, j);
      if (CODE_KEYWORDS.has(word)) {
        pushToken(tokens, "plain", line.slice(plainStart, i));
        pushToken(tokens, "keyword", word);
        plainStart = j;
      }
      i = j;
      continue;
    }

    i += 1;
  }

  if (plainStart < line.length) {
    pushToken(tokens, "plain", line.slice(plainStart));
  }

  return tokens;
}

export function highlightCodeLine(line: string): ReactNode {
  if (!line) {
    return "";
  }

  const tokens = tokenizeCodeLine(line);
  if (tokens.length === 1 && tokens[0].kind === "plain") {
    return tokens[0].text;
  }

  return tokens.map((token, idx) =>
    token.kind === "plain"
      ? token.text
      : createElement("span", { key: idx, className: TOKEN_CLASS_NAMES[token.kind] }, token.text)
  );
}
