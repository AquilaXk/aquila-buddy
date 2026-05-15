import { getOAuthUrl } from "@/lib/auth";

export default function LoginPage() {
  return (
    <section className="mx-auto max-w-md space-y-5">
      <div>
        <h1 className="text-2xl font-bold">GitHub 로그인</h1>
        <p className="mt-2 text-sm leading-6 text-neutral-700">
          GitHub OAuth 연동 후 프로필 분석과 이슈 추천을 사용할 수 있습니다.
        </p>
      </div>
      <a
        href={getOAuthUrl()}
        className="focus-ring block rounded-md bg-ink px-4 py-3 text-center text-sm font-semibold text-white"
      >
        GitHub로 계속하기
      </a>
    </section>
  );
}
