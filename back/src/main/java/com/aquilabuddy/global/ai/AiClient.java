package com.aquilabuddy.global.ai;

/**
 * AI completion 호출을 추상화하는 outbound client 계약입니다.
 */
public interface AiClient {

    /**
     * 시스템 프롬프트와 사용자 프롬프트를 전달해 생성된 응답 본문을 반환합니다.
     */
    String complete(String systemPrompt, String userPrompt);
}
