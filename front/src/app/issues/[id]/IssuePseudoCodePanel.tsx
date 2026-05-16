import type { IssuePseudoCode } from "@/lib/api";

type IssuePseudoCodePanelProps = {
  pseudoCode: IssuePseudoCode | null;
  hasError: boolean;
};

export function IssuePseudoCodePanel({ pseudoCode, hasError }: IssuePseudoCodePanelProps) {
  if (hasError) {
    return (
      <section className="surface mb-5 border-red-200 bg-red-50 p-5 text-sm font-semibold text-red-700">
        의사코드를 불러오지 못했습니다. backend API 상태를 확인해 주세요.
      </section>
    );
  }

  if (!pseudoCode) {
    return (
      <section className="surface mb-5 p-5 text-sm font-semibold text-neutral-600">
        의사코드 데이터가 준비되지 않았습니다.
      </section>
    );
  }

  return (
    <section className="surface mb-5 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-moss">Pseudo Code</p>
          <h3 className="mt-1 text-lg font-bold text-neutral-950">{pseudoCode.issueKey}</h3>
        </div>
      </div>
      <pre className="mt-4 whitespace-pre-wrap rounded-md bg-neutral-100 p-4 text-sm leading-6 text-neutral-800">
        {pseudoCode.pseudoCode}
      </pre>
    </section>
  );
}
