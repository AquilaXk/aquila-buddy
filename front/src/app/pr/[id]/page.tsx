import { ProtectedLayout } from "@/components/ProtectedLayout";

type PrDetailPageProps = {
  params: Promise<{
    id: string;
  }>;
};

export default async function PrDetailPage({ params }: PrDetailPageProps) {
  const { id } = await params;

  return (
    <ProtectedLayout title="PR Detail">
      <section className="surface p-5">
        <p className="text-sm font-semibold text-moss">Draft ID</p>
        <h2 className="mt-2 text-xl font-bold">{id}</h2>
        <p className="mt-4 text-sm leading-6 text-neutral-700">
          생성된 PR 제목, 본문, 번역 결과를 확인하고 수정하는 화면입니다.
        </p>
      </section>
    </ProtectedLayout>
  );
}
