package com.aquilabuddy.application.port.out.ai;

/**
 * AI completion 작업별 비용/품질 요구를 모델 라우팅 기준으로 표현합니다.
 */
public enum AiCompletionUseCase {
    GENERAL,
    ISSUE_SUMMARY,
    ISSUE_GUIDE,
    PR_DRAFT,
    PR_TRANSLATION
}
