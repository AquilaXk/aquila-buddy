package com.aquilabuddy.global.ai;

/**
 * Vertex AI Gemini completion 호출부를 테스트 가능한 경계로 분리합니다.
 */
public interface VertexAiTextGenerator {

    String generate(String prompt);

    default String generate(String model, String prompt) {
        return generate(prompt);
    }
}
