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
    if (searchParams.get("ext_code")) {
      return;
    }
    router.replace(callbackParams.redirectTo);
  }, [callbackParams, router, searchParams]);

  return error ? <OAuthCallbackError message={error} /> : <OAuthCallbackLoading />;
}

function OAuthCallbackFallback() {
  return <OAuthCallbackLoading />;
}

function OAuthCallbackLoading() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f6f8fa] px-4 text-[#1f2328]">
      <div className="w-full max-w-sm rounded-xl border border-[#d1d9e0] bg-[#ffffff] p-8 text-center shadow-xs">
        <div className="mx-auto mb-4 h-9 w-9 animate-spin rounded-full border-2 border-[#1f2328] border-t-transparent" />
        <p className="text-[13.5px] font-medium text-[#59636e]">로그인 처리 중...</p>
      </div>
    </div>
  );
}

function OAuthCallbackError({ message }: { message: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f6f8fa] px-4 text-[#1f2328]">
      <div className="w-full max-w-md rounded-xl border border-[#ff8182]/40 bg-[#ffffff] p-8 text-center shadow-xs">
        <div className="mx-auto mb-3 grid h-10 w-10 place-items-center rounded-full bg-[#ffebe9] text-lg font-semibold text-[#d1242f]">
          !
        </div>
        <h2 className="mb-2 text-[18px] font-semibold text-[#1f2328]">로그인 실패</h2>
        <p className="mb-6 text-[13.5px] text-[#59636e]">{message}</p>
        <a
          className="btn-primary focus-ring inline-block px-4 py-2 text-[13px]"
          href="/login"
        >
          다시 로그인
        </a>
      </div>
    </div>
  );
}
