const MAX_DIFF_PARSE_CHARS = 65_536;

export type DiffLineType = "add" | "del" | "normal" | "meta";

export type ParsedDiffLine = {
  type: DiffLineType;
  oldLine: number | null;
  newLine: number | null;
  content: string;
};

export type ParsedDiffHunk = {
  header: string;
  oldStart: number;
  newStart: number;
  lines: ParsedDiffLine[];
};

export type ParsedDiffFile = {
  oldPath: string;
  newPath: string;
  displayPath: string;
  additions: number;
  deletions: number;
  hunks: ParsedDiffHunk[];
};

export type ParsedUnifiedDiff = {
  files: ParsedDiffFile[];
  totalAdditions: number;
  totalDeletions: number;
  truncated: boolean;
};

function stripDiffPrefixPath(rawPath: string): string {
  const trimmed = rawPath.trim();
  if (trimmed.startsWith("a/") || trimmed.startsWith("b/")) {
    return trimmed.slice(2);
  }
  return trimmed;
}

function buildDisplayPath(oldPath: string, newPath: string): string {
  if (newPath && newPath !== "/dev/null" && oldPath && oldPath !== "/dev/null" && oldPath !== newPath) {
    return `${oldPath} → ${newPath}`;
  }
  if (newPath && newPath !== "/dev/null") {
    return newPath;
  }
  if (oldPath && oldPath !== "/dev/null") {
    return oldPath;
  }
  return "변경 파일";
}

function parseGitDiffHeaderPaths(line: string): { oldPath: string; newPath: string } {
  const rest = line.slice("diff --git ".length).trim();
  const aIdx = rest.indexOf(" b/");
  if (aIdx > 0) {
    const left = stripDiffPrefixPath(rest.slice(0, aIdx));
    const right = stripDiffPrefixPath(rest.slice(aIdx + 1));
    return { oldPath: left, newPath: right };
  }
  return { oldPath: "", newPath: "" };
}

function parseUintAt(source: string, start: number, limit: number): { value: number; next: number } | null {
  let i = start;
  let acc = 0;
  let digits = 0;
  while (i < limit) {
    const c = source.charCodeAt(i);
    if (c < 48 || c > 57) {
      break;
    }
    acc = acc * 10 + (c - 48);
    digits++;
    i++;
  }
  return digits > 0 ? { value: acc, next: i } : null;
}

function parseHunkLineNumbers(source: string, lineStart: number, lineEnd: number): { oldStart: number; newStart: number } {
  // Format: @@ -oldStart[,oldCount] +newStart[,newCount] @@
  const minusIdx = source.indexOf("-", lineStart + 3);
  if (minusIdx === -1 || minusIdx >= lineEnd) {
    return { oldStart: 1, newStart: 1 };
  }
  const oldParsed = parseUintAt(source, minusIdx + 1, lineEnd);
  if (!oldParsed) {
    return { oldStart: 1, newStart: 1 };
  }
  const plusIdx = source.indexOf("+", oldParsed.next);
  if (plusIdx === -1 || plusIdx >= lineEnd) {
    return { oldStart: oldParsed.value, newStart: 1 };
  }
  const newParsed = parseUintAt(source, plusIdx + 1, lineEnd);
  return {
    oldStart: oldParsed.value,
    newStart: newParsed ? newParsed.value : 1,
  };
}

/**
 * Unified Diff 명세에 따라 정규표현식 백트래킹과 split() 배열 할당 없이
 * 1-pass 인덱스로 변경 파일·청크·라인을 구조화 파싱합니다.
 */
export function parseUnifiedDiff(rawDiff: string | null | undefined): ParsedUnifiedDiff {
  if (!rawDiff || !rawDiff.trim()) {
    return { files: [], totalAdditions: 0, totalDeletions: 0, truncated: false };
  }

  let truncated = false;
  let source = rawDiff;
  if (source.length > MAX_DIFF_PARSE_CHARS) {
    const cut = source.lastIndexOf("\n", MAX_DIFF_PARSE_CHARS);
    source = source.slice(0, cut > 0 ? cut : MAX_DIFF_PARSE_CHARS);
    truncated = true;
  }

  const files: ParsedDiffFile[] = [];
  let currentFile: ParsedDiffFile | null = null;
  let currentHunk: ParsedDiffHunk | null = null;
  let oldLine = 1;
  let newLine = 1;
  let totalAdditions = 0;
  let totalDeletions = 0;

  const ensureFile = (): ParsedDiffFile => {
    if (!currentFile) {
      currentFile = {
        oldPath: "",
        newPath: "",
        displayPath: "변경 내용",
        additions: 0,
        deletions: 0,
        hunks: [],
      };
      files.push(currentFile);
    }
    return currentFile;
  };

  const ensureHunk = (file: ParsedDiffFile): ParsedDiffHunk => {
    if (!currentHunk) {
      oldLine = 1;
      newLine = 1;
      currentHunk = {
        header: "@@ -1 +1 @@",
        oldStart: 1,
        newStart: 1,
        lines: [],
      };
      file.hunks.push(currentHunk);
    }
    return currentHunk;
  };

  const len = source.length;
  let pos = 0;

  while (pos <= len) {
    let end = source.indexOf("\n", pos);
    if (end === -1) {
      end = len;
    }
    const lineEnd = end > pos && source.charCodeAt(end - 1) === 13 ? end - 1 : end;
    const lineLen = lineEnd - pos;
    const lineStart = pos;
    pos = end + 1;

    if (end === len && lineLen === 0) {
      break;
    }

    const firstChar = lineLen > 0 ? source.charCodeAt(lineStart) : 0;

    // 핫 패스 1: 공백/추가/삭제 본문 라인은 헤더 비교·문자열 슬라이스 중복 없이 즉시 처리한다.
    if (firstChar === 32 /* ' ' */ && currentHunk) {
      currentHunk.lines.push({
        type: "normal",
        oldLine,
        newLine,
        content: source.slice(lineStart + 1, lineEnd),
      });
      oldLine++;
      newLine++;
      continue;
    }

    if (firstChar === 43 /* '+' */) {
      if (
        lineLen >= 4 &&
        source.charCodeAt(lineStart + 1) === 43 &&
        source.charCodeAt(lineStart + 2) === 43 &&
        source.charCodeAt(lineStart + 3) === 32
      ) {
        const file = ensureFile();
        file.newPath = stripDiffPrefixPath(source.slice(lineStart + 4, lineEnd));
        file.displayPath = buildDisplayPath(file.oldPath, file.newPath);
        continue;
      }
      const file = currentFile ?? ensureFile();
      const hunk = currentHunk ?? ensureHunk(file);
      hunk.lines.push({
        type: "add",
        oldLine: null,
        newLine,
        content: source.slice(lineStart + 1, lineEnd),
      });
      newLine++;
      file.additions++;
      totalAdditions++;
      continue;
    }

    if (firstChar === 45 /* '-' */) {
      if (
        lineLen >= 4 &&
        source.charCodeAt(lineStart + 1) === 45 &&
        source.charCodeAt(lineStart + 2) === 45 &&
        source.charCodeAt(lineStart + 3) === 32
      ) {
        const file = ensureFile();
        file.oldPath = stripDiffPrefixPath(source.slice(lineStart + 4, lineEnd));
        file.displayPath = buildDisplayPath(file.oldPath, file.newPath);
        continue;
      }
      const file = currentFile ?? ensureFile();
      const hunk = currentHunk ?? ensureHunk(file);
      hunk.lines.push({
        type: "del",
        oldLine,
        newLine: null,
        content: source.slice(lineStart + 1, lineEnd),
      });
      oldLine++;
      file.deletions++;
      totalDeletions++;
      continue;
    }

    if (firstChar === 64 /* '@' */ && source.startsWith("@@ ", lineStart)) {
      const file = currentFile ?? ensureFile();
      const { oldStart, newStart } = parseHunkLineNumbers(source, lineStart, lineEnd);
      oldLine = oldStart;
      newLine = newStart;
      currentHunk = {
        header: source.slice(lineStart, lineEnd),
        oldStart,
        newStart,
        lines: [],
      };
      file.hunks.push(currentHunk);
      continue;
    }

    if (firstChar === 100 /* 'd' */ && source.startsWith("diff --git ", lineStart)) {
      const { oldPath, newPath } = parseGitDiffHeaderPaths(source.slice(lineStart, lineEnd));
      currentFile = {
        oldPath,
        newPath,
        displayPath: buildDisplayPath(oldPath, newPath),
        additions: 0,
        deletions: 0,
        hunks: [],
      };
      files.push(currentFile);
      currentHunk = null;
      continue;
    }

    if (
      (firstChar === 105 /* 'i' */ && source.startsWith("index ", lineStart)) ||
      (firstChar === 110 /* 'n' */ && source.startsWith("new file mode ", lineStart)) ||
      (firstChar === 100 /* 'd' */ && source.startsWith("deleted file mode ", lineStart))
    ) {
      continue;
    }

    if (firstChar === 92 /* '\\' */) {
      const file = currentFile ?? ensureFile();
      const hunk = currentHunk ?? ensureHunk(file);
      hunk.lines.push({
        type: "meta",
        oldLine: null,
        newLine: null,
        content: source.slice(lineStart, lineEnd),
      });
      continue;
    }

    if (currentHunk) {
      currentHunk.lines.push({
        type: "normal",
        oldLine,
        newLine,
        content: source.slice(lineStart, lineEnd),
      });
      oldLine++;
      newLine++;
    }
  }

  return { files, totalAdditions, totalDeletions, truncated };
}

export type SplitDiffRow =
  | { kind: "meta"; line: ParsedDiffLine }
  | { kind: "pair"; left: ParsedDiffLine | null; right: ParsedDiffLine | null };

export function pairHunkLinesForSplit(lines: ParsedDiffLine[]): SplitDiffRow[] {
  const rows: SplitDiffRow[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    if (line.type === "meta") {
      rows.push({ kind: "meta", line });
      i += 1;
      continue;
    }
    if (line.type === "normal") {
      rows.push({ kind: "pair", left: line, right: line });
      i += 1;
      continue;
    }

    const delStart = i;
    while (i < lines.length && lines[i].type === "del") {
      i += 1;
    }
    const delEnd = i;

    const addStart = i;
    while (i < lines.length && lines[i].type === "add") {
      i += 1;
    }
    const addEnd = i;

    const pairCount = Math.max(delEnd - delStart, addEnd - addStart);
    for (let k = 0; k < pairCount; k += 1) {
      rows.push({
        kind: "pair",
        left: delStart + k < delEnd ? lines[delStart + k] : null,
        right: addStart + k < addEnd ? lines[addStart + k] : null,
      });
    }
  }

  return rows;
}
