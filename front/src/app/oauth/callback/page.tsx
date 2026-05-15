"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import { setToken } from "@/lib/auth";
import { readOAuthCallbackParams } from "@/lib/oauthCallback";

type CallbackStatus = "checking" | "stored" | "missing-token" | "failed";

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
  const [status, setStatus] = useState<CallbackStatus>("checking");

  useEffect(() => {
    if (callbackParams.error) {
      setStatus("failed");
      return;
    }

    if (!callbackParams.token) {
      setStatus("missing-token");
      return;
    }

    setToken(callbackParams.token);
    setStatus("stored");
  }, [callbackParams]);

  useEffect(() => {
    if (status !== "stored") {
      return;
    }

    const redirectTimer = window.setTimeout(() => {
      router.replace(callbackParams.redirectTo);
    }, 1200);

    return () => window.clearTimeout(redirectTimer);
  }, [callbackParams.redirectTo, router, status]);

  const content = getCallbackContent(status, callbackParams.error);

  return (
    <section className="mx-auto max-w-lg space-y-5">
      <div>
        <p className="text-sm font-semibold text-moss">GitHub OAuth</p>
        <h1 className="mt-2 text-2xl font-bold">{content.title}</h1>
        <p className="mt-2 text-sm leading-6 text-neutral-700">{content.description}</p>
      </div>

      <div className={`surface p-4 ${content.panelClassName}`}>
        <p className={`text-sm font-semibold ${content.messageClassName}`}>{content.message}</p>
      </div>

      <div className="flex flex-wrap gap-3">
        <Link
          href={callbackParams.redirectTo}
          className="focus-ring inline-block rounded-md bg-moss px-4 py-2 text-sm font-semibold text-white"
        >
          계속하기
        </Link>
        {status !== "stored" ? (
          <Link
            href="/login"
            className="focus-ring inline-block rounded-md border border-line px-4 py-2 text-sm font-semibold text-neutral-700"
          >
            다시 로그인
          </Link>
        ) : null}
      </div>
    </section>
  );
}

function OAuthCallbackFallback() {
  return (
    <section className="mx-auto max-w-lg space-y-4">
      <h1 className="text-2xl font-bold">OAuth Callback</h1>
      <p className="text-sm leading-6 text-neutral-700">로그인 결과를 확인하는 중입니다.</p>
    </section>
  );
}

function getCallbackContent(status: CallbackStatus, error: string | null) {
  if (status === "stored") {
    return {
      title: "로그인이 완료되었습니다.",
      description: "GitHub OAuth token을 브라우저 세션에 저장했습니다.",
      message: "잠시 후 작업 공간으로 이동합니다.",
      panelClassName: "border-green-200 bg-green-50",
      messageClassName: "text-green-800"
    };
  }

  if (status === "failed") {
    return {
      title: "로그인을 완료하지 못했습니다.",
      description: "OAuth callback에서 오류 응답을 받았습니다.",
      message: error ?? "GitHub OAuth 오류가 발생했습니다.",
      panelClassName: "border-red-200 bg-red-50",
      messageClassName: "text-red-700"
    };
  }

  if (status === "missing-token") {
    return {
      title: "로그인 token이 없습니다.",
      description: "OAuth callback URL에서 token query를 찾지 못했습니다.",
      message: "GitHub 로그인을 다시 시작해 주세요.",
      panelClassName: "border-red-200 bg-red-50",
      messageClassName: "text-red-700"
    };
  }

  return {
    title: "로그인 결과를 확인하는 중입니다.",
    description: "OAuth callback query를 읽고 있습니다.",
    message: "잠시만 기다려 주세요.",
    panelClassName: "border-line bg-neutral-50",
    messageClassName: "text-neutral-700"
  };
}
