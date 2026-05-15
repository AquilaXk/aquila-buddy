import { ProtectedLayout } from "@/components/ProtectedLayout";

export default function ProfilePage() {
  return (
    <ProtectedLayout title="Profile">
      <section className="surface p-5">
        <h2 className="text-lg font-bold">GitHub Profile Vector</h2>
        <p className="mt-2 text-sm leading-6 text-neutral-700">
          GitHub 활동 수집, 언어 분석, 임베딩 저장 상태를 표시할 영역입니다.
        </p>
        <button className="focus-ring mt-5 rounded-md bg-amber px-4 py-2 text-sm font-semibold text-white" type="button">
          프로필 분석 요청
        </button>
      </section>
    </ProtectedLayout>
  );
}
