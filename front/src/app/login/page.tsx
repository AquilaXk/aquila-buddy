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
  const oauthUrl = getOAuthUrl(nextPath);

  return (
    <main className="flex min-h-screen items-center justify-center bg-dark-scaffold px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-xl bg-[#121317] text-white">
            <svg
              aria-hidden="true"
              className="h-6 w-6"
              fill="none"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M12 4L5.5 19H8.8L10.2 15.5H13.8L15.2 19H18.5L12 4ZM11.1 12.8L12 10.4L12.9 12.8H11.1Z"
                fill="currentColor"
              />
              <rect x="15.5" y="16.5" width="3.5" height="2.2" rx="0.6" fill="#1a73e8" />
            </svg>
          </div>
          <h1 className="mb-1.5 text-2xl font-medium tracking-[-0.015em] text-dark-text">Aquila Buddy</h1>
          <p className="text-sm text-dark-muted">오픈소스 기여 워크스페이스</p>
        </div>

        <section className="surface p-8">
          <h2 className="mb-2 text-center text-lg font-medium text-dark-text">로그인</h2>
          <p className="mb-6 text-center text-sm text-dark-muted">
            GitHub 계정으로 로그인하여 오픈소스 이슈를 탐색하세요
          </p>

          <a
            href={oauthUrl}
            className="btn-primary focus-ring flex w-full items-center justify-center gap-2.5 px-4 py-2.5 text-sm"
          >
            <GitHubIcon />
            <span>GitHub으로 로그인</span>
          </a>

          <p className="mt-5 text-center text-xs leading-relaxed text-ink-muted">
            로그인 시 GitHub 공개 프로필 및 기여 이력 조회에 동의하게 됩니다.
          </p>
        </section>
      </div>
    </main>
  );
}

function GitHubIcon() {
  return (
    <svg
      aria-hidden="true"
      fill="currentColor"
      height="18"
      viewBox="0 0 24 24"
      width="18"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path d="M12 0C5.374 0 0 5.373 0 12c0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23A11.509 11.509 0 0 1 12 5.803c1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576C20.566 21.797 24 17.3 24 12c0-6.627-5.373-12-12-12z" />
    </svg>
  );
}
