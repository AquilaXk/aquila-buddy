const MAX_BITAP_PATTERN_LENGTH = 63;
const BIT_ALL_ONES_32 = 0xffffffff;

const BIT_ANALYZED = 1 << 0;
const BIT_REPO_MATCH = 1 << 1;

export type IssueSortMode = "SCORE" | "ISSUE_ASC";
export type IssueAnalysisFilter = "ALL" | "ANALYZED" | "PENDING";

export interface FilterableIssueLike {
  repoFullName: string;
  issueNumber: number;
  title: string;
  summary?: string;
  reason?: string;
  labels: string[];
  score: number;
  isAnalyzed?: boolean;
}

export interface AggregatedIssueResult<T extends FilterableIssueLike> {
  repositoryOptions: Array<{ repoFullName: string; count: number }>;
  repoScopedTotal: number;
  analyzedCount: number;
  pendingCount: number;
  filtered: T[];
  fuzzyMatchedCount: number;
}

type CompiledBitapPattern =
  | { kind: "empty" }
  | { kind: "long"; normalizedLower: string }
  | {
      kind: "bit31";
      effectiveErrors: 0 | 1;
      matchBit: number;
      asciiMasks: Int32Array;
      extraMasks: Map<number, number> | null;
    }
  | {
      kind: "bit63";
      effectiveErrors: 0 | 1;
      matchLoMask: number;
      matchHiMask: number;
      asciiMasks: Uint32Array;
      extraMasks: Map<number, [number, number]> | null;
    };

function foldCharCode(code: number): number {
  if (code >= 65 && code <= 90) {
    return code | 0x20;
  }
  if (code > 127) {
    return String.fromCharCode(code).toLowerCase().charCodeAt(0);
  }
  return code;
}

/**
 * 검색 패턴을 32비트(1..31자) 또는 64비트(32..63자) 비트마스크로 1회 사전 컴파일한다.
 */
export function compileBitapPattern(query: string, maxErrors: 0 | 1 = 1): CompiledBitapPattern {
  const normalizedQuery = query.trim();
  if (!normalizedQuery) {
    return { kind: "empty" };
  }

  const patternLen = normalizedQuery.length;
  if (patternLen > MAX_BITAP_PATTERN_LENGTH) {
    return { kind: "long", normalizedLower: normalizedQuery.toLowerCase() };
  }

  const effectiveErrors: 0 | 1 = maxErrors > 0 && patternLen >= 4 ? 1 : 0;

  if (patternLen <= 31) {
    const asciiMasks = new Int32Array(128);
    asciiMasks.fill(-1);
    let extraMasks: Map<number, number> | null = null;

    for (let j = 0; j < patternLen; j++) {
      const code = foldCharCode(normalizedQuery.charCodeAt(j));
      const clearBit = ~(1 << j);
      if (code < 128) {
        asciiMasks[code] &= clearBit;
      } else {
        if (!extraMasks) {
          extraMasks = new Map();
        }
        extraMasks.set(code, (extraMasks.get(code) ?? -1) & clearBit);
      }
    }

    return {
      kind: "bit31",
      effectiveErrors,
      matchBit: 1 << (patternLen - 1),
      asciiMasks,
      extraMasks,
    };
  }

  const asciiMasks = new Uint32Array(256);
  asciiMasks.fill(BIT_ALL_ONES_32);
  let extraMasks: Map<number, [number, number]> | null = null;

  for (let j = 0; j < patternLen; j++) {
    const code = foldCharCode(normalizedQuery.charCodeAt(j));
    const clearLo = j < 32 ? ~(1 << j) >>> 0 : BIT_ALL_ONES_32;
    const clearHi = j >= 32 ? ~(1 << (j - 32)) >>> 0 : BIT_ALL_ONES_32;

    if (code < 128) {
      const idx = code << 1;
      asciiMasks[idx] &= clearLo;
      asciiMasks[idx + 1] &= clearHi;
    } else {
      if (!extraMasks) {
        extraMasks = new Map();
      }
      const pair = extraMasks.get(code) ?? [BIT_ALL_ONES_32, BIT_ALL_ONES_32];
      pair[0] = (pair[0] & clearLo) >>> 0;
      pair[1] = (pair[1] & clearHi) >>> 0;
      extraMasks.set(code, pair);
    }
  }

  return {
    kind: "bit63",
    effectiveErrors,
    matchLoMask: 0,
    matchHiMask: (1 << (patternLen - 33)) >>> 0,
    asciiMasks,
    extraMasks,
  };
}

function matchCompiledBitap(text: string, pattern: CompiledBitapPattern): 0 | 1 | 2 {
  if (pattern.kind === "empty") {
    return 2;
  }
  if (!text) {
    return 0;
  }
  if (pattern.kind === "long") {
    return text.toLowerCase().includes(pattern.normalizedLower) ? 2 : 0;
  }

  if (pattern.kind === "bit31") {
    const { asciiMasks, extraMasks, effectiveErrors, matchBit } = pattern;
    let r0 = -1;
    let r1 = -2;
    let fuzzyHit = false;
    const textLen = text.length;

    for (let i = 0; i < textLen; i++) {
      const rawCode = text.charCodeAt(i);
      const folded = rawCode >= 65 && rawCode <= 90 ? (rawCode | 0x20) : rawCode > 127 ? foldCharCode(rawCode) : rawCode;
      const charMask = folded < 128 ? asciiMasks[folded] : (extraMasks ? (extraMasks.get(folded) ?? -1) : -1);

      const oldR0 = r0;
      const shiftR0 = oldR0 << 1;
      r0 = shiftR0 | charMask;

      if ((r0 & matchBit) === 0) {
        return 2;
      }

      if (effectiveErrors === 1 && !fuzzyHit) {
        const shiftR1 = (r1 << 1) | charMask;
        r1 = shiftR1 & oldR0 & shiftR0 & (r0 << 1);
        if ((r1 & matchBit) === 0) {
          fuzzyHit = true;
        }
      }
    }

    return fuzzyHit ? 1 : 0;
  }

  const { asciiMasks, extraMasks, effectiveErrors, matchLoMask, matchHiMask } = pattern;
  let r0Lo = BIT_ALL_ONES_32;
  let r0Hi = BIT_ALL_ONES_32;
  let r1Lo = (BIT_ALL_ONES_32 << 1) >>> 0;
  let r1Hi = ((BIT_ALL_ONES_32 << 1) | 1) >>> 0;
  let fuzzyHit = false;
  const textLen = text.length;

  for (let i = 0; i < textLen; i++) {
    const rawCode = text.charCodeAt(i);
    const folded = rawCode >= 65 && rawCode <= 90 ? (rawCode | 0x20) : rawCode > 127 ? foldCharCode(rawCode) : rawCode;
    let charLo = BIT_ALL_ONES_32;
    let charHi = BIT_ALL_ONES_32;

    if (folded < 128) {
      const idx = folded << 1;
      charLo = asciiMasks[idx];
      charHi = asciiMasks[idx + 1];
    } else if (extraMasks) {
      const pair = extraMasks.get(folded);
      if (pair) {
        charLo = pair[0];
        charHi = pair[1];
      }
    }

    const oldR0Lo = r0Lo;
    const oldR0Hi = r0Hi;

    const shiftR0Lo = (oldR0Lo << 1) >>> 0;
    const shiftR0Hi = ((oldR0Hi << 1) | (oldR0Lo >>> 31)) >>> 0;
    r0Lo = (shiftR0Lo | charLo) >>> 0;
    r0Hi = (shiftR0Hi | charHi) >>> 0;

    if ((r0Lo & matchLoMask) === 0 && (r0Hi & matchHiMask) === 0) {
      return 2;
    }

    if (effectiveErrors === 1 && !fuzzyHit) {
      const shiftNewR0Lo = (r0Lo << 1) >>> 0;
      const shiftNewR0Hi = ((r0Hi << 1) | (r0Lo >>> 31)) >>> 0;
      const shiftR1Lo = ((r1Lo << 1) >>> 0) | charLo;
      const shiftR1Hi = (((r1Hi << 1) | (r1Lo >>> 31)) >>> 0) | charHi;

      r1Lo = (shiftR1Lo & oldR0Lo & shiftR0Lo & shiftNewR0Lo) >>> 0;
      r1Hi = (shiftR1Hi & oldR0Hi & shiftR0Hi & shiftNewR0Hi) >>> 0;

      if ((r1Lo & matchLoMask) === 0 && (r1Hi & matchHiMask) === 0) {
        fuzzyHit = true;
      }
    }
  }

  return fuzzyHit ? 1 : 0;
}

/**
 * Wu-Manber / Shift-Or 기반 64-bit(2x32-bit) 비트패턴 Bitap 매칭기입니다.
 *
 * 반환값:
 * - 2: 정확한 부분 문자열 일치
 * - 1: 최대 1오타(삽입/삭제/치환) 이내 근접 일치 (패턴 길이 4자 이상)
 * - 0: 불일치
 */
export function bitapMatch(text: string, query: string, maxErrors: 0 | 1 = 1): 0 | 1 | 2 {
  const compiled = compileBitapPattern(query, maxErrors);
  return matchCompiledBitap(text, compiled);
}

function scoreIssueWithCompiledPattern(
  issue: FilterableIssueLike,
  trimmedQuery: string,
  compiled: CompiledBitapPattern
): 0 | 1 | 2 {
  if (!trimmedQuery) {
    return 2;
  }

  if (trimmedQuery.charCodeAt(0) === 35 /* '#' */ && `#${issue.issueNumber}`.includes(trimmedQuery)) {
    return 2;
  }

  let bestScore = matchCompiledBitap(issue.title, compiled);
  if (bestScore === 2) {
    return 2;
  }

  const repoScore = matchCompiledBitap(issue.repoFullName, compiled);
  if (repoScore === 2) {
    return 2;
  }
  if (repoScore > bestScore) {
    bestScore = repoScore;
  }

  for (let i = 0; i < issue.labels.length; i++) {
    const labelScore = matchCompiledBitap(issue.labels[i], compiled);
    if (labelScore === 2) {
      return 2;
    }
    if (labelScore > bestScore) {
      bestScore = labelScore;
    }
  }

  if (issue.summary) {
    const summaryScore = matchCompiledBitap(issue.summary, compiled);
    if (summaryScore === 2) {
      return 2;
    }
    if (summaryScore > bestScore) {
      bestScore = summaryScore;
    }
  }

  if (issue.reason && bestScore < 2) {
    const reasonScore = matchCompiledBitap(issue.reason, compiled);
    if (reasonScore > bestScore) {
      bestScore = reasonScore;
    }
  }

  return bestScore;
}

export function scoreIssueQueryMatch(issue: FilterableIssueLike, query: string): 0 | 1 | 2 {
  const trimmed = query.trim();
  if (!trimmed) {
    return 2;
  }
  return scoreIssueWithCompiledPattern(issue, trimmed, compileBitapPattern(trimmed, 1));
}

/**
 * 저장소 목록 집계, 분석 상태 카운트, Bitap 검색, 정렬을 단일 패스로 처리합니다.
 */
export function filterAndAggregateIssues<T extends FilterableIssueLike>(
  issues: readonly T[],
  selectedRepo: string,
  analysisFilter: IssueAnalysisFilter,
  query: string,
  sortMode: IssueSortMode = "SCORE"
): AggregatedIssueResult<T> {
  const repoCounts = new Map<string, number>();
  const matched: Array<{ issue: T; rank: 0 | 1 | 2 }> = [];
  const trimmedQuery = query.trim();
  const hasQuery = trimmedQuery.length > 0;
  const compiledPattern = hasQuery ? compileBitapPattern(trimmedQuery, 1) : null;

  let repoScopedTotal = 0;
  let analyzedCount = 0;
  let pendingCount = 0;
  let fuzzyMatchedCount = 0;

  for (let i = 0; i < issues.length; i++) {
    const issue = issues[i];
    repoCounts.set(issue.repoFullName, (repoCounts.get(issue.repoFullName) ?? 0) + 1);

    let flags = 0;
    if (issue.isAnalyzed) {
      flags |= BIT_ANALYZED;
    }
    if (selectedRepo === "ALL" || issue.repoFullName === selectedRepo) {
      flags |= BIT_REPO_MATCH;
    }

    if ((flags & BIT_REPO_MATCH) === 0) {
      continue;
    }

    repoScopedTotal++;
    if ((flags & BIT_ANALYZED) !== 0) {
      analyzedCount++;
    } else {
      pendingCount++;
    }

    if (analysisFilter === "ANALYZED" && (flags & BIT_ANALYZED) === 0) {
      continue;
    }
    if (analysisFilter === "PENDING" && (flags & BIT_ANALYZED) !== 0) {
      continue;
    }

    const rank = compiledPattern
      ? scoreIssueWithCompiledPattern(issue, trimmedQuery, compiledPattern)
      : 2;
    if (rank === 0) {
      continue;
    }
    if (hasQuery && rank === 1) {
      fuzzyMatchedCount++;
    }
    matched.push({ issue, rank });
  }

  if (matched.length > 1) {
    matched.sort((a, b) => {
      if (b.rank !== a.rank) {
        return b.rank - a.rank;
      }
      if (sortMode === "ISSUE_ASC") {
        const repoCmp = a.issue.repoFullName.localeCompare(b.issue.repoFullName);
        if (repoCmp !== 0) {
          return repoCmp;
        }
        return a.issue.issueNumber - b.issue.issueNumber;
      }
      if (b.issue.score !== a.issue.score) {
        return b.issue.score - a.issue.score;
      }
      return a.issue.issueNumber - b.issue.issueNumber;
    });
  }

  const repositoryOptions = Array.from(repoCounts.entries(), ([repoFullName, count]) => ({
    repoFullName,
    count,
  })).sort((a, b) => a.repoFullName.localeCompare(b.repoFullName));

  return {
    repositoryOptions,
    repoScopedTotal,
    analyzedCount,
    pendingCount,
    filtered: matched.map((entry) => entry.issue),
    fuzzyMatchedCount,
  };
}
