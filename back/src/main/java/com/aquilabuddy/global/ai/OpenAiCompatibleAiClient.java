package com.aquilabuddy.global.ai;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI Chat Completions 호환 API를 호출하는 AI client입니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAiClient implements AiClient {

    private final RestClient restClient;
    private final AiProperties.OpenAiCompatible properties;

    public OpenAiCompatibleAiClient(RestClient.Builder restClientBuilder, AiProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null.")
            .openaiCompatibleOrDefault();
        this.restClient = restClientBuilder
            .baseUrl(this.properties.apiBaseUrlOrDefault())
            .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String apiKey = properties.apiKeyOrBlank();
        if (apiKey.isBlank()) {
            throw new IllegalStateException("app.ai.openai-compatible.api-key must not be blank.");
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
            properties.chatModelOrDefault(),
            List.of(
                new ChatMessage("system", requireText(systemPrompt, "systemPrompt")),
                new ChatMessage("user", requireText(userPrompt, "userPrompt"))
            ),
            properties.temperatureOrDefault()
        );

        try {
            ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
            return firstContent(response);
        } catch (RestClientException exception) {
            throw new IllegalStateException("AI completion request failed.", exception);
        }
    }

    private static String firstContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("AI completion response did not include choices.");
        }

        ChatChoice choice = response.choices().getFirst();
        String content = choice == null || choice.message() == null ? null : choice.message().content();
        return requireText(content, "AI completion content");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        double temperature
    ) {
    }

    private record ChatMessage(
        String role,
        String content
    ) {
    }

    private record ChatCompletionResponse(
        List<ChatChoice> choices
    ) {
    }

    private record ChatChoice(
        ChatMessageResponse message
    ) {
    }

    private record ChatMessageResponse(
        String content
    ) {
    }
}
