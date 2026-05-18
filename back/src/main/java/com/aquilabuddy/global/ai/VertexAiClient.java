package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiClient;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vertex AI Gemini 응답을 애플리케이션의 AI completion port로 연결합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "vertex-ai")
public class VertexAiClient implements AiClient {

    private final VertexAiTextGenerator generator;

    public VertexAiClient(VertexAiTextGenerator generator) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null.");
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String prompt = String.join("\n\n",
            "System prompt:",
            requireText(systemPrompt, "systemPrompt"),
            "User prompt:",
            requireText(userPrompt, "userPrompt")
        );
        return requireText(generator.generate(prompt), "Vertex AI completion content");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
