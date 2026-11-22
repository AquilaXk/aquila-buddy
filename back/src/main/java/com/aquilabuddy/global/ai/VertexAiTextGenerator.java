package com.aquilabuddy.global.ai;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Vertex AI Gemini completion 호출부를 테스트 가능한 경계로 분리합니다.
 */
public interface VertexAiTextGenerator {

    String generate(String prompt);

    default String generate(String model, String prompt) {
        return generate(prompt);
    }

    default String generateStream(String model, String prompt, Consumer<String> onDelta) {
        Objects.requireNonNull(onDelta, "onDelta must not be null.");
        String content = generate(model, prompt);
        if (content != null && !content.isEmpty()) {
            onDelta.accept(content);
        }
        return content;
    }
}
