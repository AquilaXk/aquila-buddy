import Link from "next/link";

export default function OAuthCallbackPage() {
  return (
    <section className="mx-auto max-w-lg space-y-4">
      <h1 className="text-2xl font-bold">OAuth Callback</h1>
      <p className="text-sm leading-6 text-neutral-700">
        백엔드 OAuth 완료 후 JWT 저장 흐름을 연결할 자리입니다.
      </p>
      <Link href="/dashboard" className="focus-ring inline-block rounded-md bg-moss px-4 py-2 text-sm font-semibold text-white">
        대시보드로 이동
      </Link>
    </section>
  );
}
