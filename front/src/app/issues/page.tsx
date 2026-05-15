import Link from "next/link";
import { ProtectedLayout } from "@/components/ProtectedLayout";

const sampleIssues = [
  {
    id: "demo-1",
    repo: "vercel/next.js",
    title: "Improve contributor-facing documentation",
    reason: "문서 중심 첫 기여 흐름 확인용 샘플"
  },
  {
    id: "demo-2",
    repo: "spring-projects/spring-boot",
    title: "Refine validation error example",
    reason: "Java/Spring 관심사와 맞는 샘플"
  }
];

export default function IssuesPage() {
  return (
    <ProtectedLayout title="Recommended Issues">
      <div className="space-y-3">
        {sampleIssues.map((issue) => (
          <Link
            key={issue.id}
            href={`/issues/${issue.id}`}
            className="focus-ring surface block p-4 hover:border-moss"
          >
            <p className="text-sm font-semibold text-moss">{issue.repo}</p>
            <h2 className="mt-1 text-lg font-bold">{issue.title}</h2>
            <p className="mt-2 text-sm leading-6 text-neutral-700">{issue.reason}</p>
          </Link>
        ))}
      </div>
    </ProtectedLayout>
  );
}
