"use client";

import { useEffect, useState } from "react";
import { FileCode, GitCommitHorizontal, Loader2 } from "@/components/icons";
import {
  getLocalGitBranch,
  getLocalGitDiff,
  getLocalGitStatus,
  isTauriDesktop,
  type LocalGitStatusEntry,
} from "@/lib/desktop";
import { cn } from "@/lib/utils";

const STORAGE_KEY = "aquila-buddy-local-repo-dir";
const MAX_VISIBLE_FILES = 8;

type LocalGitBridgePanelProps = {
  onBranchDetected: (branch: string) => void;
  onLocalDiffChange: (diffContent: string) => void;
};

/**
 * Desktop-only local Git repository connector for reading working-tree diffs
 * into PR draft creation without requiring manual diff uploads on the web.
 */
export function LocalGitBridgePanel({
  onBranchDetected,
  onLocalDiffChange,
}: LocalGitBridgePanelProps) {
  const [desktopReady, setDesktopReady] = useState(false);
  const [repoDir, setRepoDir] = useState("");
  const [branch, setBranch] = useState<string | null>(null);
  const [entries, setEntries] = useState<LocalGitStatusEntry[] | null>(null);
  const [loadedDiff, setLoadedDiff] = useState("");
  const [selectedFile, setSelectedFile] = useState<string | null>(null);
  const [useLocalDiff, setUseLocalDiff] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isTauriDesktop()) {
      return;
    }
    setDesktopReady(true);
    try {
      const saved = window.localStorage.getItem(STORAGE_KEY);
      if (saved) {
        setRepoDir(saved);
      }
    } catch {
      // Ignore storage access restriction
    }
  }, []);

  if (!desktopReady) {
    return null;
  }

  async function loadRepositoryState(fileFilter?: string) {
    const trimmed = repoDir.trim();
    if (!trimmed) {
      setError("로컬 Git 클론 경로를 입력해주세요.");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      window.localStorage.setItem(STORAGE_KEY, trimmed);
    } catch {
      // Ignore storage write error
    }

    try {
      const [detectedBranch, statusEntries, diffText] = await Promise.all([
        getLocalGitBranch(trimmed),
        getLocalGitStatus(trimmed),
        getLocalGitDiff(trimmed, fileFilter),
      ]);
      setBranch(detectedBranch || null);
      setEntries(statusEntries);
      setSelectedFile(fileFilter ?? null);
      setLoadedDiff(diffText);
      const hasDiff = diffText.trim().length > 0;
      setUseLocalDiff(hasDiff);
      onLocalDiffChange(hasDiff ? diffText : "");
      if (detectedBranch && detectedBranch !== "HEAD") {
        onBranchDetected(detectedBranch);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "로컬 저장소를 읽지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }

  function handleToggleUseDiff(checked: boolean) {
    setUseLocalDiff(checked);
    onLocalDiffChange(checked && loadedDiff.trim() ? loadedDiff : "");
  }

  const diffBytes = loadedDiff ? new TextEncoder().encode(loadedDiff).length : 0;

  return (
    <div className="space-y-2.5 border-b border-[#d1d9e0] bg-[#f6f8fa]/60 p-4">
      <div className="flex items-center justify-between gap-2">
        <span className="inline-flex items-center gap-1.5 text-[12.5px] font-semibold text-[#1f2328]">
          <GitCommitHorizontal className="h-3.5 w-3.5 text-[#59636e]" />
          <span>로컬 Git 저장소 연동</span>
        </span>
        {branch ? (
          <span className="rounded-full border border-[#d1d9e0] bg-white px-2 py-0.5 font-mono text-[11px] font-medium text-[#0969da]">
            {branch}
          </span>
        ) : null}
      </div>

      <div className="flex gap-1.5">
        <input
          aria-label="로컬 Git 클론 경로"
          className="focus-ring min-w-0 flex-1 rounded-md border border-[#d1d9e0] bg-white px-2.5 py-1.5 font-mono text-[11.5px] text-[#1f2328] placeholder:text-[#59636e] focus:border-[#0969da]"
          onChange={(event) => setRepoDir(event.target.value)}
          placeholder="/path/to/cloned-repo"
          type="text"
          value={repoDir}
        />
        <button
          className="btn-secondary focus-ring inline-flex shrink-0 items-center gap-1 px-2.5 py-1.5 text-[11.5px] font-medium disabled:cursor-not-allowed disabled:opacity-50"
          disabled={loading}
          onClick={() => loadRepositoryState()}
          type="button"
        >
          {loading ? <Loader2 className="h-3 w-3 animate-spin" /> : null}
          <span>{loading ? "읽는 중..." : "로컬 변경사항 불러오기"}</span>
        </button>
      </div>

      {error ? (
        <p className="rounded-md border border-[#ff8182]/40 bg-[#ffebe9] px-2.5 py-1.5 text-[11.5px] font-medium text-[#d1242f]">
          {error}
        </p>
      ) : null}

      {entries !== null ? (
        <div className="space-y-2 rounded-md border border-[#d1d9e0] bg-white p-2.5 text-[11.5px]">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="tabular-nums font-medium text-[#1f2328]">
              {entries.length === 0 ? "변경 파일 없음 (clean)" : `변경 파일 ${entries.length}건`}
            </span>
            <span className="tabular-nums font-mono text-[#59636e]">
              {diffBytes.toLocaleString("en-US")} B diff
            </span>
          </div>

          {entries.length > 0 ? (
            <div className="flex flex-wrap gap-1">
              {selectedFile ? (
                <button
                  className="focus-ring rounded border border-[#0969da]/30 bg-[#ddf4ff] px-1.5 py-0.5 font-sans text-[11px] font-medium text-[#0969da]"
                  onClick={() => loadRepositoryState()}
                  type="button"
                >
                  전체 diff 보기
                </button>
              ) : null}
              {entries.slice(0, MAX_VISIBLE_FILES).map((entry) => {
                const active = selectedFile === entry.path;
                return (
                  <button
                    className={cn(
                      "focus-ring inline-flex max-w-full items-center gap-1 rounded border px-1.5 py-0.5 font-mono text-[10.5px] transition-colors",
                      active
                        ? "border-[#0969da]/35 bg-[#ddf4ff] text-[#0969da]"
                        : "border-[#d1d9e0] bg-[#f6f8fa] text-[#1f2328] hover:border-[#b9c3ce]"
                    )}
                    key={`${entry.status}:${entry.path}`}
                    onClick={() => loadRepositoryState(entry.path)}
                    title={entry.path}
                    type="button"
                  >
                    <FileCode className="h-3 w-3 shrink-0 text-[#59636e]" />
                    <span className="max-w-[170px] truncate">{entry.path}</span>
                    <span className="text-[#59636e]">{entry.status}</span>
                  </button>
                );
              })}
            </div>
          ) : null}

          {diffBytes > 0 ? (
            <label className="flex cursor-pointer items-center gap-2 pt-0.5 text-[11.5px] font-medium text-[#1f2328]">
              <input
                checked={useLocalDiff}
                className="h-3.5 w-3.5 rounded border-[#d1d9e0] accent-[#0969da]"
                onChange={(event) => handleToggleUseDiff(event.target.checked)}
                type="checkbox"
              />
              <span>로컬 Diff로 PR 초안 생성</span>
            </label>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
