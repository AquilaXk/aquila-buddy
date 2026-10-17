"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { markLoggedIn } from "@/lib/auth";
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

    markLoggedIn();
    router.replace(callbackParams.redirectTo);
  }, [callbackParams, router]);

  return error ? <OAuthCallbackError message={error} /> : <OAuthCallbackLoading />;
}

function OAuthCallbackFallback() {
  return <OAuthCallbackLoading />;
}

function OAuthCallbackLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-dark-scaffold">
      <div className="text-center">
        <div className="mx-auto mb-4 h-9 w-9 animate-spin rounded-full border-2 border-[#121317] border-t-transparent" />
        <p className="text-sm text-dark-muted">로그인 처리 중...</p>
      </div>
    </div>
  );
}

function OAuthCallbackError({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-dark-scaffold px-4">
      <div className="surface w-full max-w-md border-[#c5221f]/25 p-8 text-center">
        <div className="mx-auto mb-3 grid h-10 w-10 place-items-center rounded-full bg-[#fce8e6] text-lg font-semibold text-[#c5221f]">
          !
        </div>
        <h2 className="mb-2 text-lg font-medium text-dark-text">로그인 실패</h2>
        <p className="mb-6 text-sm text-dark-muted">{message}</p>
        <a
          className="btn-primary focus-ring inline-block px-4 py-2 text-sm"
          href="/login"
        >
          다시 로그인
        </a>
      </div>
    </div>
  );
}
