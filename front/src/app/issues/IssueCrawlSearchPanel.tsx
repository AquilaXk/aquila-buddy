"use client";

import { FormEvent, useState } from "react";
import { crawlSearchIssues, type RecommendedIssue } from "@/lib/api";

const defaultQuery = "language:java label:\"good first issue\" state:open";

export function IssueCrawlSearchPanel() {
  const [query, setQuery] = useState(defaultQuery);
  const [results, setResults] = useState<RecommendedIssue[]>([]);
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setSearched(false);

    try {
      const issues = await crawlSearchIssues(query.trim());
      setResults(issues);
      setSearched(true);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "이슈 후보 수집에 실패했습니다.");
      setResults([]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="surface p-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-github-accent">Issue Search</p>
          <h2 className="mt-1 text-base font-bold text-github-text">Good First Issue 후보 수집</h2>
        </div>
        <span className="text-xs font-semibold text-github-muted">{results.length} results</span>
      </div>

      <form className="mt-4 flex flex-col gap-3 md:flex-row" onSubmit={handleSubmit}>
        <input
          className="focus-ring min-w-0 flex-1 rounded-md border border-github-border px-3 py-2 text-sm"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="language:java label:&quot;good first issue&quot; state:open"
          value={query}
        />
        <button
          className="focus-ring rounded-md bg-github-accent px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
          disabled={loading || !query.trim()}
          type="submit"
        >
          {loading ? "수집 중..." : "수집"}
        </button>
      </form>

      {error ? (
        <div className="mt-3 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm font-semibold text-red-400">
          {error}
        </div>
      ) : null}

      {searched && results.length === 0 ? (
        <p className="mt-3 text-sm font-semibold text-github-muted">수집된 후보가 없습니다.</p>
      ) : null}

      {results.length > 0 ? (
        <div className="mt-4 space-y-2">
          {results.map((issue) => (
            <article className="rounded-md border border-github-border p-3" key={issue.id}>
              <div className="flex flex-wrap items-center gap-2">
                <p className="break-all text-xs font-semibold text-github-accent">
                  {issue.repoFullName} #{issue.issueNumber}
                </p>
                <span className="rounded-md bg-github-bg px-2 py-1 text-xs font-semibold text-github-muted">
                  {issue.status}
                </span>
              </div>
              <h3 className="mt-2 text-sm font-bold text-github-text">{issue.title}</h3>
              <p className="mt-2 line-clamp-2 text-sm leading-6 text-github-muted">{issue.summary}</p>
            </article>
          ))}
        </div>
      ) : null}
    </section>
  );
}
