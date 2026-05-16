import { getOAuthUrl } from "@/lib/auth";
import { normalizeInternalRedirect } from "@/lib/oauthCallback";

type LoginPageProps = {
  searchParams?: Promise<{
    next?: string;
  }>;
};

export default async function LoginPage({ searchParams }: LoginPageProps) {
  const resolvedSearchParams = await searchParams;
  const nextPath = normalizeInternalRedirect(resolvedSearchParams?.next ?? null);

  return (
    <main className="flex min-h-screen items-center justify-center bg-github-bg px-4 py-10 text-github-text">
      <section className="w-full max-w-md rounded-xl border border-github-border bg-github-surface p-8 shadow-2xl shadow-black/30">
        <p className="text-sm font-semibold text-github-purple-light">OMOS</p>
        <h1 className="mt-3 text-2xl font-bold text-github-text">GitHub 로그인</h1>
        <p className="mt-3 text-sm leading-6 text-github-muted">
          GitHub OAuth 연동 후 프로필 분석과 이슈 추천을 사용할 수 있습니다.
        </p>
        <a
          href={getOAuthUrl(nextPath)}
          className="focus-ring mt-6 block rounded-md bg-github-accent px-4 py-3 text-center text-sm font-semibold text-white transition-colors hover:bg-github-accent/85"
        >
          GitHub로 계속하기
        </a>
      </section>
    </main>
  );
}
