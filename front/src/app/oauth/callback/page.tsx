"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { setToken } from "@/lib/auth";
import { readOAuthCallbackParams } from "@/lib/oauthCallback";

export default function OAuthCallbackPage() {
  return (
    <Suspense fallback={<OAuthCallbackFallback />}>
      <OAuthCallbackContent />
    </Suspense>
  );
}

function OAuthCallbackContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const callbackParams = useMemo(() => readOAuthCallbackParams(searchParams), [searchParams]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (callbackParams.error) {
      setError(callbackParams.error);
      return;
    }

    if (!callbackParams.token) {
      setError("토큰을 받지 못했습니다. 백엔드 로그를 확인하거나 다시 로그인해 주세요.");
      return;
    }

    setToken(callbackParams.token);
    router.replace(callbackParams.redirectTo);
  }, [callbackParams, router]);

  return error ? <OAuthCallbackError message={error} /> : <OAuthCallbackLoading />;
}

function OAuthCallbackFallback() {
  return <OAuthCallbackLoading />;
}

function OAuthCallbackLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-github-bg">
      <div className="text-center">
        <div className="mx-auto mb-4 h-10 w-10 border-2 border-github-purple-light border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-github-muted">로그인 처리 중...</p>
      </div>
    </div>
  );
}

function OAuthCallbackError({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-github-bg px-4">
      <div className="w-full max-w-md rounded-xl border border-red-500/40 bg-github-surface p-8 text-center">
        <div className="mb-4 text-4xl text-red-400">!</div>
        <h2 className="mb-2 text-lg font-semibold text-github-text">로그인 실패</h2>
        <p className="mb-6 text-sm text-github-muted">{message}</p>
        <a
          className="inline-block rounded-lg bg-github-purple px-4 py-2 text-sm text-white transition-colors hover:bg-github-purple/90"
          href="/login"
        >
          다시 로그인
        </a>
      </div>
    </div>
  );
}
