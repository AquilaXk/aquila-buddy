import { ProtectedLayout } from "@/components/ProtectedLayout";

type IssueDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function IssueDetailPage({ params }: IssueDetailPageProps) {
  const { id } = await params;

  return (
    <ProtectedLayout title="Issue Guide">
      <section className="surface p-5">
        <p className="text-sm font-semibold text-moss">Issue ID</p>
        <h2 className="mt-2 text-xl font-bold">{id}</h2>
        <div className="mt-5 grid gap-4 md:grid-cols-3">
          <div>
            <h3 className="text-sm font-semibold">Guide</h3>
            <p className="mt-2 text-sm leading-6 text-neutral-700">AI 코드 수정 가이드가 표시될 영역입니다.</p>
          </div>
          <div>
            <h3 className="text-sm font-semibold">Pseudo Code</h3>
            <p className="mt-2 text-sm leading-6 text-neutral-700">의사 코드와 구현 순서를 표시합니다.</p>
          </div>
          <div>
            <h3 className="text-sm font-semibold">Side Effects</h3>
            <p className="mt-2 text-sm leading-6 text-neutral-700">수정 영향 범위를 정리합니다.</p>
          </div>
        </div>
      </section>
    </ProtectedLayout>
  );
}
