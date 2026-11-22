"use client";

import { useId, useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Columns2, FileCode, Rows3 } from "./icons";
import { highlightCodeLine } from "@/lib/highlightCodeLine";
import { computeInlineDiffSegments, type InlineDiffSegment } from "@/lib/inlineDiff";
import {
  pairHunkLinesForSplit,
  parseUnifiedDiff,
  type ParsedDiffFile,
  type ParsedDiffLine,
} from "@/lib/parseUnifiedDiff";
import { cn } from "@/lib/utils";

const FOLD_LINE_THRESHOLD = 120;

type DiffViewMode = "unified" | "split";

function normalizeRepoPath(rawPath: string): string {
  const trimmed = rawPath.trim().replaceAll("\\", "/");
  return trimmed.startsWith("./") ? trimmed.slice(2) : trimmed;
}

function isHighlightedFile(
  file: ParsedDiffFile,
  normalizedTargets: Set<string>,
  targetList: string[]
): boolean {
  if (normalizedTargets.size === 0) {
    return false;
  }
  const candidates = [file.newPath, file.oldPath, file.displayPath];
  for (let i = 0; i < candidates.length; i += 1) {
    const candidate = normalizeRepoPath(candidates[i]);
    if (!candidate) {
      continue;
    }
    if (normalizedTargets.has(candidate)) {
      return true;
    }
    for (let j = 0; j < targetList.length; j += 1) {
      const target = targetList[j];
      if (candidate.endsWith(`/${target}`) || target.endsWith(`/${candidate}`)) {
        return true;
      }
    }
  }
  return false;
}

function DiffStatSquares({ additions, deletions }: { additions: number; deletions: number }) {
  const total = additions + deletions;
  if (total <= 0) {
    return null;
  }
  const greenCount = Math.min(5, Math.round((additions / total) * 5));
  const redCount = Math.min(5 - greenCount, Math.round((deletions / total) * 5));

  return (
    <span aria-hidden="true" className="inline-flex items-center gap-0.5">
      {[0, 1, 2, 3, 4].map((slot) => (
        <span
          className={cn(
            "h-2 w-2 rounded-[1px]",
            slot < greenCount
              ? "bg-[#1f883d]"
              : slot < greenCount + redCount
                ? "bg-[#cf222e]"
                : "bg-[#d1d9e0]"
          )}
          key={slot}
        />
      ))}
    </span>
  );
}

export function DiffViewer({
  diffContent,
  highlightPaths = [],
}: {
  diffContent: string;
  highlightPaths?: string[];
}) {
  const baseId = useId();
  const parsed = useMemo(() => parseUnifiedDiff(diffContent), [diffContent]);
  const [viewMode, setViewMode] = useState<DiffViewMode>("unified");
  const [ignoreWhitespace, setIgnoreWhitespace] = useState(false);
  const [collapsedFiles, setCollapsedFiles] = useState<Record<number, boolean>>({});
  const [visibleLineLimits, setVisibleLineLimits] = useState<Record<number, number>>({});

  const { normalizedTargets, targetList } = useMemo(() => {
    const set = new Set<string>();
    const list: string[] = [];
    for (let i = 0; i < highlightPaths.length; i += 1) {
      const normalized = normalizeRepoPath(highlightPaths[i]);
      if (normalized && !set.has(normalized)) {
        set.add(normalized);
        list.push(normalized);
      }
    }
    return { normalizedTargets: set, targetList: list };
  }, [highlightPaths]);

  if (parsed.files.length === 0) {
    return (
      <pre className="max-h-72 overflow-auto whitespace-pre-wrap rounded-md border border-[#d1d9e0] bg-[#f6f8fa] p-4 font-mono text-[12.5px] leading-[1.55] text-[#1f2328]">
        {diffContent}
      </pre>
    );
  }

  function jumpToFile(fileIndex: number) {
    setCollapsedFiles((prev) => (prev[fileIndex] ? { ...prev, [fileIndex]: false } : prev));
    const el = document.getElementById(`${baseId}-file-${fileIndex}`);
    el?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  return (
    <div className="space-y-3">
      <div className="rounded-lg border border-[#d1d9e0] bg-[#f6f8fa] px-3.5 py-2.5 text-xs">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="font-medium text-[#1f2328]">
            변경 파일{" "}
            <span className="tabular-nums font-mono font-semibold text-[#0969da]">
              {parsed.files.length}
            </span>
            개
          </span>
          <div className="flex flex-wrap items-center gap-2 font-mono text-xs">
            <span className="tabular-nums rounded-full border border-[#1a7f37]/30 bg-[#dafbe1] px-2 py-0.5 font-medium text-[#1a7f37]">
              +{parsed.totalAdditions}
            </span>
            <span className="tabular-nums rounded-full border border-[#d1242f]/25 bg-[#ffebe9] px-2 py-0.5 font-medium text-[#d1242f]">
              -{parsed.totalDeletions}
            </span>
            {parsed.truncated ? (
              <span className="rounded-full border border-[#9a6700]/25 bg-[#fff8c5] px-2 py-0.5 font-sans text-[11px] font-medium text-[#9a6700]">
                64KB 상한으로 일부 생략됨
              </span>
            ) : null}

            <button
              aria-pressed={ignoreWhitespace}
              className={cn(
                "focus-ring rounded-md border px-2 py-0.5 font-sans text-[11px] transition-colors",
                ignoreWhitespace
                  ? "border-[#0969da]/30 bg-[#ddf4ff] font-semibold text-[#0969da]"
                  : "border-[#d1d9e0] bg-white font-medium text-[#59636e] hover:text-[#1f2328]"
              )}
              onClick={() => setIgnoreWhitespace((prev) => !prev)}
              type="button"
            >
              공백 무시
            </button>

            <div
              aria-label="Diff 보기 형식"
              className="flex items-center gap-0.5 rounded-md border border-[#d1d9e0] bg-[#eaeef2] p-0.5 font-sans"
              role="group"
            >
              <button
                aria-pressed={viewMode === "unified"}
                className={cn(
                  "focus-ring inline-flex items-center gap-1 rounded-[5px] px-2 py-0.5 text-[11px] transition-colors",
                  viewMode === "unified"
                    ? "border border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
                    : "border border-transparent font-medium text-[#59636e] hover:text-[#1f2328]"
                )}
                onClick={() => setViewMode("unified")}
                type="button"
              >
                <Rows3 className="h-3 w-3" />
                <span>통합</span>
              </button>
              <button
                aria-pressed={viewMode === "split"}
                className={cn(
                  "focus-ring inline-flex items-center gap-1 rounded-[5px] px-2 py-0.5 text-[11px] transition-colors",
                  viewMode === "split"
                    ? "border border-[#d1d9e0] bg-white font-semibold text-[#1f2328] shadow-xs"
                    : "border border-transparent font-medium text-[#59636e] hover:text-[#1f2328]"
                )}
                onClick={() => setViewMode("split")}
                type="button"
              >
                <Columns2 className="h-3 w-3" />
                <span>분할</span>
              </button>
            </div>
          </div>
        </div>

        <div className="mt-2.5 flex flex-wrap gap-1.5 border-t border-[#d1d9e0] pt-2.5">
          {parsed.files.map((file, fileIndex) => {
            const highlighted = isHighlightedFile(file, normalizedTargets, targetList);
            return (
              <button
                className={cn(
                  "focus-ring inline-flex max-w-full items-center gap-1.5 rounded-md border px-2 py-0.5 font-mono text-[11px] transition-colors",
                  highlighted
                    ? "border-[#0969da]/30 bg-[#ddf4ff] text-[#0969da]"
                    : "border-[#d1d9e0] bg-white text-[#1f2328] hover:border-[#b9c3ce] hover:bg-[#eff2f5]"
                )}
                key={`pill-${file.displayPath}-${fileIndex}`}
                onClick={() => jumpToFile(fileIndex)}
                type="button"
              >
                <span className="max-w-44 truncate">{file.displayPath}</span>
                <span className="tabular-nums text-[#1a7f37]">+{file.additions}</span>
                <span className="tabular-nums text-[#d1242f]">-{file.deletions}</span>
                {highlighted ? (
                  <span className="rounded-full bg-[#0969da] px-1.5 py-0 font-sans text-[10px] font-semibold text-white">
                    이슈 연관
                  </span>
                ) : null}
              </button>
            );
          })}
        </div>
      </div>

      {parsed.files.map((file, fileIndex) => {
        const highlighted = isHighlightedFile(file, normalizedTargets, targetList);
        const limit = visibleLineLimits[fileIndex] ?? FOLD_LINE_THRESHOLD;
        return (
          <DiffFilePanel
            file={file}
            fileId={`${baseId}-file-${fileIndex}`}
            fileIndex={fileIndex}
            highlighted={highlighted}
            ignoreWhitespace={ignoreWhitespace}
            isCollapsed={Boolean(collapsedFiles[fileIndex])}
            key={`${file.displayPath}-${fileIndex}`}
            onExpandFull={(totalLines) =>
              setVisibleLineLimits((prev) => ({ ...prev, [fileIndex]: totalLines }))
            }
            onLoadMore={(nextLimit) =>
              setVisibleLineLimits((prev) => ({ ...prev, [fileIndex]: nextLimit }))
            }
            onResetFold={() =>
              setVisibleLineLimits((prev) => ({ ...prev, [fileIndex]: FOLD_LINE_THRESHOLD }))
            }
            onToggleCollapse={() =>
              setCollapsedFiles((prev) => ({ ...prev, [fileIndex]: !prev[fileIndex] }))
            }
            viewMode={viewMode}
            visibleLineLimit={limit}
          />
        );
      })}
    </div>
  );
}

function DiffFilePanel({
  file,
  fileId,
  fileIndex,
  highlighted,
  ignoreWhitespace,
  isCollapsed,
  viewMode,
  visibleLineLimit,
  onToggleCollapse,
  onLoadMore,
  onExpandFull,
  onResetFold,
}: {
  file: ParsedDiffFile;
  fileId: string;
  fileIndex: number;
  highlighted: boolean;
  ignoreWhitespace: boolean;
  isCollapsed: boolean;
  viewMode: DiffViewMode;
  visibleLineLimit: number;
  onToggleCollapse: () => void;
  onLoadMore: (nextLimit: number) => void;
  onExpandFull: (totalLines: number) => void;
  onResetFold: () => void;
}) {
  const totalLines = useMemo(
    () => file.hunks.reduce((sum, hunk) => sum + hunk.lines.length, 0),
    [file.hunks]
  );
  const needsFold = visibleLineLimit < totalLines;
  let remainingLines = needsFold ? visibleLineLimit : Number.MAX_SAFE_INTEGER;

  return (
    <div
      className={cn(
        "overflow-hidden rounded-lg border bg-white shadow-xs",
        highlighted ? "border-[#0969da]/50" : "border-[#d1d9e0]",
        fileIndex >= 2 && "studio-deferred-diff"
      )}
      id={fileId}
    >
      <button
        aria-expanded={!isCollapsed}
        className={cn(
          "focus-ring sticky top-0 z-10 flex w-full items-center justify-between gap-3 border-b px-3.5 py-2 text-left text-xs transition-colors",
          highlighted
            ? "border-[#0969da]/25 bg-[#ddf4ff]/70 hover:bg-[#ddf4ff]"
            : "border-[#d1d9e0] bg-[#f6f8fa] hover:bg-[#eff2f5]"
        )}
        onClick={onToggleCollapse}
        type="button"
      >
        <span className="flex min-w-0 items-center gap-1.5 font-mono text-xs font-semibold text-[#1f2328]">
          {isCollapsed ? (
            <ChevronRight className="h-3.5 w-3.5 shrink-0 text-[#59636e]" />
          ) : (
            <ChevronDown className="h-3.5 w-3.5 shrink-0 text-[#59636e]" />
          )}
          <FileCode className="h-3.5 w-3.5 shrink-0 text-[#59636e]" />
          <span className="truncate">{file.displayPath}</span>
          {highlighted ? (
            <span className="badge-trim shrink-0 rounded-full border border-[#0969da]/25 bg-[#ddf4ff] px-2 py-0.5 font-sans text-[10.5px] font-semibold text-[#0969da]">
              이슈 연관
            </span>
          ) : null}
        </span>
        <span className="tabular-nums flex shrink-0 items-center gap-2 font-mono text-[11.5px]">
          <DiffStatSquares additions={file.additions} deletions={file.deletions} />
          <span className="font-medium text-[#1a7f37]">+{file.additions}</span>
          <span className="font-medium text-[#d1242f]">-{file.deletions}</span>
        </span>
      </button>

      {!isCollapsed ? (
        <div className="overflow-x-auto font-mono text-[12.5px] leading-[1.55]">
          {file.hunks.length === 0 ? (
            <div className="px-4 py-3 font-sans text-xs text-[#59636e]">표시할 코드 hunk가 없습니다.</div>
          ) : (
            file.hunks.map((hunk, hunkIdx) => {
              if (remainingLines <= 0) {
                return null;
              }
              const visibleLines = needsFold
                ? hunk.lines.slice(0, remainingLines)
                : hunk.lines;
              remainingLines -= visibleLines.length;

              return (
                <div key={`${hunk.header}-${hunkIdx}`}>
                  <div className="flex items-center border-y border-[#d1d9e0] bg-[#ddf4ff]">
                    <span className="w-[5.25rem] shrink-0 select-none bg-[#b6e3ff] px-2 py-1 text-center font-mono text-[11px] text-[#59636e]">
                      @@
                    </span>
                    <span className="px-3 py-1 font-mono text-[11.5px] text-[#59636e]">
                      {hunk.header}
                    </span>
                  </div>
                  {viewMode === "split" ? (
                    <SplitHunkTable ignoreWhitespace={ignoreWhitespace} lines={visibleLines} />
                  ) : (
                    <UnifiedHunkTable ignoreWhitespace={ignoreWhitespace} lines={visibleLines} />
                  )}
                </div>
              );
            })
          )}

          {totalLines > FOLD_LINE_THRESHOLD ? (
            <div className="flex flex-wrap items-center justify-center gap-3 border-t border-[#d1d9e0] bg-[#f6f8fa] px-3 py-2 text-center font-sans">
              {needsFold ? (
                <>
                  {visibleLineLimit + FOLD_LINE_THRESHOLD < totalLines ? (
                    <button
                      className="focus-ring text-xs font-medium text-[#0969da] hover:underline"
                      onClick={() => onLoadMore(visibleLineLimit + FOLD_LINE_THRESHOLD)}
                      type="button"
                    >
                      다음 {FOLD_LINE_THRESHOLD}줄 더 보기 ({visibleLineLimit}/{totalLines})
                    </button>
                  ) : null}
                  <button
                    className="focus-ring text-xs font-medium text-[#0969da] hover:underline"
                    onClick={() => onExpandFull(totalLines)}
                    type="button"
                  >
                    전체 줄 펼치기 ({totalLines}줄)
                  </button>
                </>
              ) : (
                <button
                  className="focus-ring text-xs font-medium text-[#0969da] hover:underline"
                  onClick={onResetFold}
                  type="button"
                >
                  120줄 미리보기로 접기
                </button>
              )}
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

function renderCodeWithInlineDiff(
  content: string,
  segment: InlineDiffSegment | null,
  tone: "add" | "del" | null
) {
  if (!segment || !segment.changed || !tone) {
    return highlightCodeLine(content);
  }
  const markClass =
    tone === "add"
      ? "rounded-[2px] bg-[var(--diffBlob-additionWord-bgColor,#aceebb)] text-inherit"
      : "rounded-[2px] bg-[var(--diffBlob-deletionWord-bgColor,#ffcecb)] text-inherit";
  return (
    <>
      {segment.prefix ? highlightCodeLine(segment.prefix) : null}
      <mark className={markClass}>{highlightCodeLine(segment.changed)}</mark>
      {segment.suffix ? highlightCodeLine(segment.suffix) : null}
    </>
  );
}

function UnifiedHunkTable({
  lines,
  ignoreWhitespace,
}: {
  lines: ParsedDiffLine[];
  ignoreWhitespace: boolean;
}) {
  const inlinePairs = useMemo(() => {
    const map = new Map<number, { seg: InlineDiffSegment; whitespaceOnly: boolean }>();
    for (let i = 0; i < lines.length - 1; i += 1) {
      const cur = lines[i];
      const nxt = lines[i + 1];
      if (
        cur.type === "del" &&
        nxt.type === "add" &&
        lines[i - 1]?.type !== "del" &&
        lines[i + 2]?.type !== "add"
      ) {
        const res = computeInlineDiffSegments(cur.content, nxt.content, ignoreWhitespace);
        if (res) {
          map.set(i, { seg: res.oldSegments, whitespaceOnly: res.whitespaceOnly });
          map.set(i + 1, { seg: res.newSegments, whitespaceOnly: res.whitespaceOnly });
        }
      }
    }
    return map;
  }, [ignoreWhitespace, lines]);

  return (
    <table className="w-full border-collapse">
      <tbody>
        {lines.map((line, lineIdx) => {
          const paired = inlinePairs.get(lineIdx);
          const mutedByWhitespace = Boolean(paired?.whitespaceOnly);
          const rowBg =
            mutedByWhitespace
              ? "bg-white text-[#59636e]"
              : line.type === "add"
                ? "bg-[#dafbe1] text-[#1f2328]"
                : line.type === "del"
                  ? "bg-[#ffebe9] text-[#1f2328]"
                  : line.type === "meta"
                    ? "bg-[#f6f8fa] text-[#59636e]"
                    : "bg-white text-[#1f2328]";
          const gutterBg =
            mutedByWhitespace
              ? "bg-[#ffffff] text-[#59636e]"
              : line.type === "add"
                ? "bg-[#aceebb] text-[#1f2328]"
                : line.type === "del"
                  ? "bg-[#ffcecb] text-[#1f2328]"
                  : "bg-[#ffffff] text-[#59636e]";
          const marker =
            line.type === "add"
              ? "+"
              : line.type === "del"
                ? "-"
                : line.type === "meta"
                  ? "\\"
                  : " ";
          const markerColor =
            mutedByWhitespace
              ? "text-[#818b98]"
              : line.type === "add"
                ? "text-[#1a7f37]"
                : line.type === "del"
                  ? "text-[#d1242f]"
                  : "text-[#59636e]";
          const tone =
            !mutedByWhitespace && (line.type === "add" || line.type === "del") ? line.type : null;

          return (
            <tr className={rowBg} key={lineIdx}>
              <td
                className={`tabular-nums sticky left-0 z-[1] w-10 min-w-[2.5rem] select-none border-r border-[#d1d9e0] px-2 text-right text-[11px] ${gutterBg}`}
              >
                {line.oldLine ?? ""}
              </td>
              <td
                className={`tabular-nums sticky left-10 z-[1] w-10 min-w-[2.5rem] select-none border-r border-[#d1d9e0] px-2 text-right text-[11px] ${gutterBg}`}
              >
                {line.newLine ?? ""}
              </td>
              <td className={`w-6 select-none px-1.5 text-center font-semibold ${markerColor}`}>
                {marker}
              </td>
              <td className="whitespace-pre pr-4">
                {renderCodeWithInlineDiff(line.content, paired?.seg ?? null, tone)}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function SplitHunkTable({
  lines,
  ignoreWhitespace,
}: {
  lines: ParsedDiffLine[];
  ignoreWhitespace: boolean;
}) {
  const rows = useMemo(() => pairHunkLinesForSplit(lines), [lines]);

  return (
    <table className="w-full table-fixed border-collapse">
      <tbody>
        {rows.map((row, idx) => {
          if (row.kind === "meta") {
            return (
              <tr className="bg-[#f6f8fa] text-[#59636e]" key={idx}>
                <td className="px-3 py-0.5" colSpan={4}>
                  {row.line.content}
                </td>
              </tr>
            );
          }

          const inline =
            row.left?.type === "del" && row.right?.type === "add"
              ? computeInlineDiffSegments(row.left.content, row.right.content, ignoreWhitespace)
              : null;
          const mutedByWhitespace = Boolean(inline?.whitespaceOnly);

          const leftBg =
            mutedByWhitespace
              ? "bg-white text-[#59636e]"
              : row.left?.type === "del"
                ? "bg-[#ffebe9] text-[#1f2328]"
                : row.left
                  ? "bg-white text-[#1f2328]"
                  : "bg-[#f6f8fa]";
          const leftGutter =
            mutedByWhitespace
              ? "bg-white text-[#59636e]"
              : row.left?.type === "del"
                ? "bg-[#ffcecb] text-[#1f2328]"
                : row.left
                  ? "bg-white text-[#59636e]"
                  : "bg-[#f6f8fa] text-[#59636e]";
          const rightBg =
            mutedByWhitespace
              ? "bg-white text-[#59636e]"
              : row.right?.type === "add"
                ? "bg-[#dafbe1] text-[#1f2328]"
                : row.right
                  ? "bg-white text-[#1f2328]"
                  : "bg-[#f6f8fa]";
          const rightGutter =
            mutedByWhitespace
              ? "bg-white text-[#59636e]"
              : row.right?.type === "add"
                ? "bg-[#aceebb] text-[#1f2328]"
                : row.right
                  ? "bg-white text-[#59636e]"
                  : "bg-[#f6f8fa] text-[#59636e]";

          return (
            <tr className="border-b border-[#d1d9e0]/50 last:border-b-0" key={idx}>
              <td className={`tabular-nums w-10 select-none border-r border-[#d1d9e0] px-2 text-right text-[11px] ${leftGutter}`}>
                {row.left?.oldLine ?? ""}
              </td>
              <td className={`w-1/2 overflow-x-auto whitespace-pre border-r border-[#d1d9e0] px-2 ${leftBg}`}>
                {row.left
                  ? renderCodeWithInlineDiff(
                      row.left.content,
                      inline?.oldSegments ?? null,
                      mutedByWhitespace ? null : row.left.type === "del" ? "del" : null
                    )
                  : ""}
              </td>
              <td className={`tabular-nums w-10 select-none border-r border-[#d1d9e0] px-2 text-right text-[11px] ${rightGutter}`}>
                {row.right?.newLine ?? ""}
              </td>
              <td className={`w-1/2 overflow-x-auto whitespace-pre px-2 ${rightBg}`}>
                {row.right
                  ? renderCodeWithInlineDiff(
                      row.right.content,
                      inline?.newSegments ?? null,
                      mutedByWhitespace ? null : row.right.type === "add" ? "add" : null
                    )
                  : ""}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
