import { ProtectedLayout } from "@/components/ProtectedLayout";

export default function PrDraftPage() {
  return (
    <ProtectedLayout title="PR Draft">
      <form className="surface space-y-4 p-5">
        <label className="block">
          <span className="text-sm font-semibold">Repository</span>
          <input className="focus-ring mt-2 w-full rounded-md border border-line px-3 py-2" placeholder="owner/repo" />
        </label>
        <label className="block">
          <span className="text-sm font-semibold">Diff</span>
          <textarea className="focus-ring mt-2 min-h-40 w-full rounded-md border border-line px-3 py-2" placeholder="git diff 내용을 입력" />
        </label>
        <button className="focus-ring rounded-md bg-moss px-4 py-2 text-sm font-semibold text-white" type="button">
          초안 생성
        </button>
      </form>
    </ProtectedLayout>
  );
}
