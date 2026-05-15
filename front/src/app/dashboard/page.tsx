import { ProtectedLayout } from "@/components/ProtectedLayout";

const lanes = [
  { label: "Profile", value: "대기", detail: "GitHub OAuth 연동 후 분석 가능" },
  { label: "Issues", value: "0", detail: "추천 이슈 API 연결 전" },
  { label: "PR Drafts", value: "0", detail: "초안 생성 이력 없음" }
];

export default function DashboardPage() {
  return (
    <ProtectedLayout title="Dashboard">
      <div className="grid gap-4 md:grid-cols-3">
        {lanes.map((lane) => (
          <section key={lane.label} className="surface p-4">
            <p className="text-sm font-semibold text-neutral-500">{lane.label}</p>
            <p className="mt-3 text-2xl font-bold text-ink">{lane.value}</p>
            <p className="mt-2 text-sm leading-6 text-neutral-700">{lane.detail}</p>
          </section>
        ))}
      </div>
    </ProtectedLayout>
  );
}
