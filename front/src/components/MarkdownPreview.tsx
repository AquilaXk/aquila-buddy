"use client";

import type { ReactNode } from "react";
import { ExternalBrowserLink } from "@/components/ExternalBrowserLink";
import { highlightCodeLine } from "@/lib/highlightCodeLine";
import { cn } from "@/lib/utils";

export const MAX_MARKDOWN_CHARS = 65_536;

type InlineToken =
  | { kind: "text"; text: string }
  | { kind: "code"; text: string }
  | { kind: "bold"; text: string }
  | { kind: "link"; label: string; href: string };

type ListEntry = {
  text: string;
  checked: boolean | null;
};

type MarkdownBlock =
  | { kind: "heading"; level: number; text: string }
  | { kind: "code"; lang: string; code: string }
  | { kind: "quote"; lines: string[] }
  | { kind: "list"; ordered: boolean; items: ListEntry[] }
  | { kind: "table"; headers: string[]; rows: string[][] }
  | { kind: "hr" }
  | { kind: "paragraph"; text: string };

function isSafeHttpUrl(rawHref: string): boolean {
  const trimmed = rawHref.trim();
  return /^https?:\/\/[^\s<>"'`]+$/i.test(trimmed);
}

/**
 * Tokenizes inline markdown spans (`code`, **bold**, and https links) in O(n)
 * without catastrophic backtracking or raw HTML injection.
 */
export function parseInlineMarkdown(text: string): InlineToken[] {
  const tokens: InlineToken[] = [];
  const len = text.length;
  let pos = 0;
  let textStart = 0;

  const flushText = (end: number) => {
    if (end > textStart) {
      tokens.push({ kind: "text", text: text.slice(textStart, end) });
    }
  };

  while (pos < len) {
    const ch = text.charCodeAt(pos);
    if (ch === 96 /* ` */) {
      const close = text.indexOf("`", pos + 1);
      if (close !== -1) {
        flushText(pos);
        tokens.push({ kind: "code", text: text.slice(pos + 1, close) });
        pos = close + 1;
        textStart = pos;
        continue;
      }
    } else if (ch === 42 /* * */ && text.charCodeAt(pos + 1) === 42) {
      const close = text.indexOf("**", pos + 2);
      if (close > pos + 2) {
        flushText(pos);
        tokens.push({ kind: "bold", text: text.slice(pos + 2, close) });
        pos = close + 2;
        textStart = pos;
        continue;
      }
    } else if (ch === 91 /* [ */) {
      const closeLabel = text.indexOf("]", pos + 1);
      if (closeLabel > pos + 1 && text.charCodeAt(closeLabel + 1) === 40 /* ( */) {
        const closeUrl = text.indexOf(")", closeLabel + 2);
        if (closeUrl > closeLabel + 2) {
          const href = text.slice(closeLabel + 2, closeUrl).trim();
          const label = text.slice(pos + 1, closeLabel);
          if (isSafeHttpUrl(href)) {
            flushText(pos);
            tokens.push({ kind: "link", label, href });
            pos = closeUrl + 1;
            textStart = pos;
            continue;
          }
        }
      }
    }
    pos += 1;
  }

  flushText(len);
  return tokens;
}

function splitTableRow(line: string): string[] {
  let trimmed = line.trim();
  if (trimmed.startsWith("|")) trimmed = trimmed.slice(1);
  if (trimmed.endsWith("|")) trimmed = trimmed.slice(0, -1);
  return trimmed.split("|").map((cell) => cell.trim());
}

function isTableSeparatorLine(line: string): boolean {
  const trimmed = line.trim();
  if (!trimmed.includes("-") || !trimmed.includes("|")) {
    return false;
  }
  const cells = splitTableRow(trimmed);
  return cells.length > 0 && cells.every((cell) => /^:?-{3,}:?$/.test(cell));
}

function parseListLine(line: string): { ordered: boolean; text: string; checked: boolean | null } | null {
  const taskMatch = line.match(/^\s{0,3}(?:[-*+])\s+\[([ xX])\]\s+(.*)$/);
  if (taskMatch) {
    return {
      ordered: false,
      checked: taskMatch[1].toLowerCase() === "x",
      text: taskMatch[2],
    };
  }
  const unorderedMatch = line.match(/^\s{0,3}(?:[-*+])\s+(.*)$/);
  if (unorderedMatch) {
    return { ordered: false, checked: null, text: unorderedMatch[1] };
  }
  const orderedMatch = line.match(/^\s{0,3}\d+\.\s+(.*)$/);
  if (orderedMatch) {
    return { ordered: true, checked: null, text: orderedMatch[1] };
  }
  return null;
}

/**
 * Parses GFM markdown blocks up to MAX_MARKDOWN_CHARS into deterministic React block descriptors.
 */
export function parseMarkdownBlocks(rawContent: string): { blocks: MarkdownBlock[]; truncated: boolean } {
  const truncated = rawContent.length > MAX_MARKDOWN_CHARS;
  const bounded = truncated ? rawContent.slice(0, MAX_MARKDOWN_CHARS) : rawContent;
  const lines = bounded.replaceAll("\r\n", "\n").split("\n");
  const blocks: MarkdownBlock[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed) {
      i += 1;
      continue;
    }

    const fenceMatch = trimmed.match(/^```([A-Za-z0-9_+-]*)/);
    if (fenceMatch) {
      const lang = fenceMatch[1] ?? "";
      const codeLines: string[] = [];
      i += 1;
      while (i < lines.length && !lines[i].trim().startsWith("```")) {
        codeLines.push(lines[i]);
        i += 1;
      }
      if (i < lines.length) i += 1;
      blocks.push({ kind: "code", lang, code: codeLines.join("\n") });
      continue;
    }

    const headingMatch = trimmed.match(/^(#{1,6})\s+(.+)$/);
    if (headingMatch) {
      blocks.push({ kind: "heading", level: headingMatch[1].length, text: headingMatch[2] });
      i += 1;
      continue;
    }

    if (/^(?:-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      blocks.push({ kind: "hr" });
      i += 1;
      continue;
    }

    if (trimmed.includes("|") && i + 1 < lines.length && isTableSeparatorLine(lines[i + 1])) {
      const headers = splitTableRow(trimmed);
      const rows: string[][] = [];
      i += 2;
      while (i < lines.length && lines[i].trim().includes("|") && lines[i].trim() !== "") {
        rows.push(splitTableRow(lines[i]));
        i += 1;
      }
      blocks.push({ kind: "table", headers, rows });
      continue;
    }

    if (trimmed.startsWith(">")) {
      const quoteLines: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith(">")) {
        quoteLines.push(lines[i].trim().replace(/^>\s?/, ""));
        i += 1;
      }
      blocks.push({ kind: "quote", lines: quoteLines });
      continue;
    }

    const firstList = parseListLine(line);
    if (firstList) {
      const ordered = firstList.ordered;
      const items: ListEntry[] = [];
      while (i < lines.length) {
        const parsed = parseListLine(lines[i]);
        if (!parsed || parsed.ordered !== ordered) break;
        items.push({ text: parsed.text, checked: parsed.checked });
        i += 1;
      }
      blocks.push({ kind: "list", ordered, items });
      continue;
    }

    const paraLines: string[] = [];
    while (i < lines.length) {
      const cur = lines[i];
      const curTrim = cur.trim();
      if (
        !curTrim ||
        curTrim.startsWith("```") ||
        curTrim.startsWith(">") ||
        /^(#{1,6})\s+/.test(curTrim) ||
        /^(?:-{3,}|\*{3,}|_{3,})$/.test(curTrim) ||
        parseListLine(cur) !== null
      ) {
        break;
      }
      paraLines.push(curTrim);
      i += 1;
    }
    if (paraLines.length > 0) {
      blocks.push({ kind: "paragraph", text: paraLines.join(" ") });
    } else {
      i += 1;
    }
  }

  return { blocks, truncated };
}

function renderInline(text: string): ReactNode {
  const tokens = parseInlineMarkdown(text);
  return tokens.map((token, idx) => {
    if (token.kind === "code") {
      return (
        <code
          className="rounded border border-[#d1d9e0] bg-[#eff2f5] px-1 py-0.5 font-mono text-[12px] text-[#1f2328]"
          key={idx}
        >
          {token.text}
        </code>
      );
    }
    if (token.kind === "bold") {
      return (
        <strong className="font-semibold text-[#1f2328]" key={idx}>
          {token.text}
        </strong>
      );
    }
    if (token.kind === "link") {
      return (
        <ExternalBrowserLink
          className="font-medium text-[#0969da] underline hover:text-[#0550ae]"
          href={token.href}
          key={idx}
        >
          {token.label}
        </ExternalBrowserLink>
      );
    }
    return token.text;
  });
}

export function MarkdownPreview({
  content,
  className,
}: {
  content: string;
  className?: string;
}) {
  const { blocks, truncated } = parseMarkdownBlocks(content);

  if (blocks.length === 0) {
    return null;
  }

  return (
    <div className={cn("space-y-3 text-[13.5px] leading-[1.6] text-[#1f2328]", className)}>
      {blocks.map((block, index) => {
        if (block.kind === "heading") {
          const sizeClass =
            block.level <= 2
              ? "border-b border-[#d1d9e0] pb-1 text-[15px] font-semibold tracking-[-0.015em]"
              : "text-[14px] font-semibold";
          return (
            <p className={cn(sizeClass, "text-[#1f2328]")} key={index}>
              {renderInline(block.text)}
            </p>
          );
        }

        if (block.kind === "code") {
          return (
            <pre
              className="overflow-x-auto rounded-md border border-[#d1d9e0] bg-[#ffffff] p-3 font-mono text-[12.5px] leading-[1.55] text-[#1f2328]"
              key={index}
            >
              {block.code.split("\n").map((line, lineIdx) => (
                <div key={lineIdx}>{highlightCodeLine(line) || " "}</div>
              ))}
            </pre>
          );
        }

        if (block.kind === "quote") {
          return (
            <blockquote
              className="border-l-[3px] border-[#d1d9e0] pl-3 text-[#59636e]"
              key={index}
            >
              {block.lines.map((line, lineIdx) => (
                <p key={lineIdx}>{renderInline(line)}</p>
              ))}
            </blockquote>
          );
        }

        if (block.kind === "hr") {
          return <hr className="border-t border-[#d1d9e0]" key={index} />;
        }

        if (block.kind === "table") {
          return (
            <div className="table-wrapper rounded-md border border-[#d1d9e0]" key={index}>
              <table className="w-full border-collapse text-left text-xs">
                <thead className="bg-[#f6f8fa]">
                  <tr className="border-b border-[#d1d9e0]">
                    {block.headers.map((header, colIdx) => (
                      <th className="px-3 py-2 font-semibold text-[#1f2328]" key={colIdx}>
                        {renderInline(header)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#d1d9e0] bg-white">
                  {block.rows.map((row, rowIdx) => (
                    <tr key={rowIdx}>
                      {block.headers.map((_, colIdx) => (
                        <td className="px-3 py-2 text-[#1f2328]" key={colIdx}>
                          {renderInline(row[colIdx] ?? "")}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        }

        if (block.kind === "list") {
          const hasTasks = block.items.some((item) => item.checked !== null);
          const Tag = block.ordered ? "ol" : "ul";
          return (
            <Tag
              className={cn(
                "space-y-1.5",
                hasTasks ? "list-none pl-0" : block.ordered ? "list-decimal pl-5" : "list-disc pl-5"
              )}
              key={index}
            >
              {block.items.map((item, itemIdx) => (
                <li className={cn(hasTasks && "flex items-start gap-2")} key={itemIdx}>
                  {item.checked !== null ? (
                    <span
                      aria-label={item.checked ? "완료됨" : "미완료"}
                      className={cn(
                        "mt-1 inline-flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded border text-[10px] font-semibold",
                        item.checked
                          ? "border-[#1f883d] bg-[#1f883d] text-white"
                          : "border-[#b9c3ce] bg-white text-transparent"
                      )}
                    >
                      ✓
                    </span>
                  ) : null}
                  <span>{renderInline(item.text)}</span>
                </li>
              ))}
            </Tag>
          );
        }

        return (
          <p className="text-pretty text-[#1f2328]" key={index}>
            {renderInline(block.text)}
          </p>
        );
      })}

      {truncated ? (
        <p className="rounded border border-[#9a6700]/25 bg-[#fff8c5] px-2.5 py-1 text-xs font-medium text-[#9a6700]">
          문서 길이 상한(64KB)으로 이후 내용은 생략되었습니다.
        </p>
      ) : null}
    </div>
  );
}
