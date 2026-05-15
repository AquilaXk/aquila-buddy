import Link from "next/link";

const steps = [
  {
    title: "프로필 분석",
    text: "GitHub 활동과 언어 사용을 수집해 추천 기준을 준비합니다."
  },
  {
    title: "이슈 추천",
    text: "Good First Issue 후보를 사용자 프로필과 비교해 우선순위를 만듭니다."
  },
  {
    title: "PR 초안",
    text: "diff와 컨벤션을 바탕으로 제목과 본문 초안을 생성합니다."
  }
];

export default function HomePage() {
  return (
    <div className="grid gap-6 lg:grid-cols-[1.2fr_0.8fr]">
      <section className="space-y-5">
        <div>
          <p className="text-sm font-semibold text-moss">Aquila Buddy</p>
          <h1 className="mt-2 text-3xl font-bold tracking-normal text-ink">
            오픈소스 기여 흐름을 한 화면에서 관리합니다.
          </h1>
          <p className="mt-3 max-w-2xl text-base leading-7 text-neutral-700">
            GitHub 프로필 분석, 맞춤 이슈 추천, 코드 수정 가이드, PR 초안 생성을 순서대로
            처리하는 작업 공간입니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-3">
          <Link
            href="/dashboard"
            className="focus-ring rounded-md bg-moss px-4 py-2 text-sm font-semibold text-white"
          >
            대시보드 열기
          </Link>
          <Link
            href="/issues"
            className="focus-ring rounded-md border border-line px-4 py-2 text-sm font-semibold"
          >
            이슈 후보 보기
          </Link>
        </div>
      </section>
      <section className="surface divide-y divide-line">
        {steps.map((step, index) => (
          <div key={step.title} className="grid grid-cols-[40px_1fr] gap-4 p-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-md bg-neutral-100 text-sm font-bold text-moss">
              {index + 1}
            </div>
            <div>
              <h2 className="text-base font-semibold">{step.title}</h2>
              <p className="mt-1 text-sm leading-6 text-neutral-700">{step.text}</p>
            </div>
          </div>
        ))}
      </section>
    </div>
  );
}
