"use client";

import { Suspense, useState, type MouseEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { getOAuthUrl, setDesktopAccessToken } from "@/lib/auth";
import { isTauriDesktop, startDesktopPkceLogin } from "@/lib/desktop";
import { normalizeInternalRedirect } from "@/lib/oauthCallback";

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginPageContent />
    </Suspense>
  );
}

function LoginPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const nextPath = normalizeInternalRedirect(searchParams.get("next"));
  const oauthUrl = getOAuthUrl(nextPath);
  const [desktopPending, setDesktopPending] = useState(false);
  const [desktopError, setDesktopError] = useState<string | null>(null);

  async function handleOAuthClick(event: MouseEvent<HTMLAnchorElement>) {
    if (!isTauriDesktop()) {
      return;
    }

    event.preventDefault();
    if (desktopPending) {
      return;
    }

    setDesktopPending(true);
    setDesktopError(null);
    try {
      const token = await startDesktopPkceLogin(oauthUrl);
      setDesktopAccessToken(token);
      router.replace(nextPath || "/dashboard");
    } catch (caught) {
      setDesktopError(caught instanceof Error ? caught.message : "데스크톱 로그인에 실패했습니다.");
    } finally {
      setDesktopPending(false);
    }
  }

  return (
    <main className="grid min-h-screen grid-cols-1 bg-[#ffffff] text-[#1f2328] lg:grid-cols-2">
      <section className="hidden flex-col justify-between bg-[#0d1117] px-10 py-10 text-[#f0f6fc] lg:flex">
        <div className="flex items-center gap-2.5">
          <span className="grid h-8 w-8 place-items-center rounded-md border border-[#30363d] bg-[#161b22] text-[#f0f6fc]">
            <svg
              aria-hidden="true"
              className="h-4 w-4"
              fill="none"
              viewBox="0 0 24 24"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M12 4L5.5 19H8.8L10.2 15.5H13.8L15.2 19H18.5L12 4ZM11.1 12.8L12 10.4L12.9 12.8H11.1Z"
                fill="currentColor"
              />
              <rect x="15.5" y="16.5" width="3.5" height="2.2" rx="0.6" fill="#2f81f7" />
            </svg>
          </span>
          <div>
            <p className="text-[14px] font-semibold tracking-[-0.02em] text-[#f0f6fc]">Aquila Buddy</p>
            <p className="text-xs text-[#8b949e]">오픈소스 기여 워크스페이스</p>
          </div>
        </div>

        <div className="my-auto max-w-lg space-y-6 py-8">
          <div className="space-y-3">
            <h2 className="text-[28px] font-semibold leading-[1.3] tracking-[-0.03em] text-[#f0f6fc] break-keep">
              이슈 탐색부터 PR 초안까지, 하나의 개발 워크플로우
            </h2>
            <p className="text-[14px] leading-[1.6] text-[#8b949e]">
              GitHub 활동과 기술 스택을 바탕으로 알맞은 이슈를 추천하고, 코드 맥락 분석과 PR 초안 작성을 바로 연결합니다.
            </p>
          </div>

          <div className="space-y-3">
            <div className="rounded-lg border border-[#30363d] bg-[#161b22] p-4">
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="rounded-full border border-[#3fb950]/40 bg-[#238636]/20 px-2 py-0.5 font-mono text-[11px] font-semibold text-[#3fb950]">
                    OPEN
                  </span>
                  <span className="font-mono text-xs text-[#8b949e]">AquilaXk/aquila-buddy #261</span>
                </div>
                <span className="tabular-nums rounded-full border border-[#3fb950]/40 bg-[#238636]/20 px-2 py-0.5 font-mono text-[11px] font-semibold text-[#3fb950]">
                  94% MATCH
                </span>
              </div>
              <p className="mt-2 text-[14px] font-semibold text-[#f0f6fc]">
                추천 이슈 맥락 분석 및 수정 대상 파일 추출 개선
              </p>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-lg border border-[#30363d] bg-[#161b22] p-3.5">
                <p className="font-mono text-[11px] font-medium text-[#58a6ff]">CODE CONTEXT</p>
                <p className="mt-1.5 text-[13px] font-semibold text-[#f0f6fc]">수정 대상 파일 &amp; 의사 코드</p>
                <p className="mt-1 font-mono text-[11.5px] text-[#8b949e]">
                  src/app/issues/[id]/page.tsx
                </p>
              </div>
              <div className="rounded-lg border border-[#30363d] bg-[#161b22] p-3.5">
                <p className="font-mono text-[11px] font-medium text-[#3fb950]">PR DRAFT</p>
                <p className="mt-1.5 text-[13px] font-semibold text-[#f0f6fc]">영문 번역 &amp; 준비도 점검</p>
                <p className="mt-1 font-mono text-[11.5px] text-[#8b949e]">
                  main ← feat/issue-261
                </p>
              </div>
            </div>

            <div className="overflow-hidden rounded-lg border border-[#30363d] bg-[#161b22]">
              <div className="flex items-center justify-between border-b border-[#30363d] px-3.5 py-2 font-mono text-[11.5px] text-[#8b949e]">
                <span>diff --git a/src/lib/recommend.ts b/src/lib/recommend.ts</span>
                <span className="tabular-nums text-[#3fb950]">+2 -1</span>
              </div>
              <div className="bg-[#0d1117] px-3.5 py-2.5 font-mono text-[12px] leading-[1.55]">
                <p className="bg-[#f85149]/15 px-1.5 text-[#ff7b72]">- const limit = 5;</p>
                <p className="bg-[#2ea043]/15 px-1.5 text-[#3fb950]">+ const limit = Math.min(maxCandidates, 12);</p>
                <p className="bg-[#2ea043]/15 px-1.5 text-[#3fb950]">+ return rankByContextSimilarity(issues, limit);</p>
              </div>
            </div>
          </div>
        </div>

        <p className="font-mono text-xs text-[#8b949e]">
          GitHub Issues · Code Context · Pull Request Draft
        </p>
      </section>

      <section className="flex items-center justify-center bg-[#ffffff] px-6 py-12">
        <div className="w-full max-w-[380px]">
          <div className="mb-7 text-center">
            <div className="mx-auto mb-3 grid h-11 w-11 place-items-center rounded-lg bg-[#1f2328] text-white shadow-xs">
              <svg
                aria-hidden="true"
                className="h-5 w-5"
                fill="none"
                viewBox="0 0 24 24"
                xmlns="http://www.w3.org/2000/svg"
              >
                <path
                  d="M12 4L5.5 19H8.8L10.2 15.5H13.8L15.2 19H18.5L12 4ZM11.1 12.8L12 10.4L12.9 12.8H11.1Z"
                  fill="currentColor"
                />
                <rect x="15.5" y="16.5" width="3.5" height="2.2" rx="0.6" fill="#0969da" />
              </svg>
            </div>
            <h1 className="text-[22px] font-semibold tracking-[-0.025em] text-[#1f2328]">Aquila Buddy</h1>
            <p className="mt-1 text-[13.5px] text-[#59636e]">오픈소스 기여 워크스페이스</p>
          </div>

          <div className="overflow-hidden rounded-xl border border-[#d1d9e0] bg-[#ffffff] shadow-xs">
            <div className="flex items-center justify-between border-b border-[#d1d9e0] bg-[#f6f8fa] px-5 py-2.5 text-xs">
              <span className="font-semibold text-[#1f2328]">계정 연동</span>
              <span className="font-mono text-[#59636e]">GitHub OAuth 2.0</span>
            </div>
            <div className="p-7">
              <h2 className="mb-2 text-center text-[24px] font-semibold tracking-[-0.025em] text-[#1f2328]">
                로그인
              </h2>
              <p className="mb-6 text-center text-[13.5px] leading-[1.55] text-[#59636e]">
                GitHub 계정으로 로그인하여 오픈소스 이슈를 탐색하세요
              </p>

              <a
                aria-disabled={desktopPending}
                className="btn-primary focus-ring flex w-full items-center justify-center gap-2.5 px-4 py-2.5 text-[13.5px]"
                href={oauthUrl}
                onClick={handleOAuthClick}
              >
                <GitHubIcon />
                <span>{desktopPending ? "브라우저에서 로그인 진행 중..." : "GitHub으로 로그인"}</span>
              </a>

              {desktopError ? (
                <p className="mt-3 rounded-md border border-[#ff8182]/40 bg-[#ffebe9] px-3 py-2 text-xs text-[#d1242f]">
                  {desktopError}
                </p>
              ) : null}

              <div className="mt-5 border-t border-[#d1d9e0] pt-4">
                <p className="text-center text-xs leading-relaxed text-[#59636e]">
                  로그인 시 GitHub 공개 프로필 및 기여 이력 조회에 동의하게 됩니다.
                </p>
              </div>
            </div>
          </div>
        </div>
      </section>
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
