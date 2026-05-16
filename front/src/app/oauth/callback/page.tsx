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

  if (status === "checking") {
    return <OAuthCallbackLoading />;
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-github-bg px-4 py-10 text-github-text">
      <section className="w-full max-w-lg rounded-xl border border-github-border bg-github-surface p-8 shadow-2xl shadow-black/30">
        <div>
          <p className="text-sm font-semibold text-github-purple-light">GitHub OAuth</p>
          <h1 className="mt-3 text-2xl font-bold text-github-text">{content.title}</h1>
          <p className="mt-3 text-sm leading-6 text-github-muted">{content.description}</p>
        </div>

        <div className={`mt-5 rounded-md border px-4 py-3 ${content.panelClassName}`}>
          <p className={`text-sm font-semibold ${content.messageClassName}`}>{content.message}</p>
        </div>

        <div className="mt-6 flex flex-wrap gap-3">
          <Link
            href={callbackParams.redirectTo}
            className="focus-ring inline-block rounded-md bg-github-accent px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-github-accent/85"
          >
            계속하기
          </Link>
          {status !== "stored" ? (
            <Link
              href="/login"
              className="focus-ring inline-block rounded-md border border-github-border px-4 py-2 text-sm font-semibold text-github-muted transition-colors hover:border-github-accent hover:text-github-accent"
            >
              다시 로그인
            </Link>
          ) : null}
        </div>
      </section>
    </main>
  );
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

function getCallbackContent(status: CallbackStatus, error: string | null) {
  if (status === "stored") {
    return {
      title: "로그인이 완료되었습니다.",
      description: "GitHub OAuth token을 브라우저 세션에 저장했습니다.",
      message: "잠시 후 작업 공간으로 이동합니다.",
      panelClassName: "border-green-500/30 bg-green-500/10",
      messageClassName: "text-green-400"
    };
  }

  if (status === "failed") {
    return {
      title: "로그인을 완료하지 못했습니다.",
      description: "OAuth callback에서 오류 응답을 받았습니다.",
      message: error ?? "GitHub OAuth 오류가 발생했습니다.",
      panelClassName: "border-red-500/30 bg-red-500/10",
      messageClassName: "text-red-400"
    };
  }

  if (status === "missing-token") {
    return {
      title: "로그인 token이 없습니다.",
      description: "OAuth callback URL에서 token query를 찾지 못했습니다.",
      message: "GitHub 로그인을 다시 시작해 주세요.",
      panelClassName: "border-red-500/30 bg-red-500/10",
      messageClassName: "text-red-400"
    };
  }

  return {
    title: "로그인 결과를 확인하는 중입니다.",
    description: "OAuth callback query를 읽고 있습니다.",
    message: "잠시만 기다려 주세요.",
    panelClassName: "border-github-border bg-github-bg",
    messageClassName: "text-github-muted"
  };
}
