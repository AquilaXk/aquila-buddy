package com.aquilabuddy.application.port.out.ai;

import java.util.Objects;

/**
 * AI completion 호출을 추상화하는 outbound client 계약입니다.
 */
public interface AiClient {

    /**
     * 시스템 프롬프트와 사용자 프롬프트를 전달해 생성된 응답 본문을 반환합니다.
     */
    String complete(String systemPrompt, String userPrompt);

    /**
     * 작업 종류를 함께 전달해 provider가 적절한 completion 모델을 고를 수 있게 합니다.
     */
    default String complete(AiCompletionUseCase useCase, String systemPrompt, String userPrompt) {
        Objects.requireNonNull(useCase, "useCase must not be null.");
        return complete(systemPrompt, userPrompt);
    }
}
