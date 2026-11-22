package com.aquilabuddy.application.port.out.ai;

import java.util.Objects;
import java.util.function.Consumer;

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

    /**
     * 토큰 델타를 수신하는 콜백과 함께 스트리밍 completion을 수행하고 누적 응답을 반환합니다.
     */
    default String stream(
        AiCompletionUseCase useCase,
        String systemPrompt,
        String userPrompt,
        Consumer<String> onDelta
    ) {
        Objects.requireNonNull(useCase, "useCase must not be null.");
        Objects.requireNonNull(onDelta, "onDelta must not be null.");
        String content = complete(useCase, systemPrompt, userPrompt);
        if (content != null && !content.isEmpty()) {
            onDelta.accept(content);
        }
        return content;
    }
}
