package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * OpenAI Chat Completions 호환 API를 호출하는 AI client입니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAiClient implements AiClient, EmbeddingClient {

    private static final String PROVIDER = "openai-compatible";
    private static final String COMPLETION_PROMPT_VERSION = "ai-client.complete.v1";
    private static final String EMBEDDING_PROMPT_VERSION = "ai-client.embed.v1";

    private final RestClient restClient;
    private final OpenAiCompatibleProperties properties;
    private final AiCallObserver observer;

    public OpenAiCompatibleAiClient(RestClient.Builder restClientBuilder, OpenAiCompatibleProperties properties) {
        this(restClientBuilder, properties, new NoopAiCallObserver());
    }

    @Autowired
    public OpenAiCompatibleAiClient(
        RestClient.Builder restClientBuilder,
        OpenAiCompatibleProperties properties,
        ObjectProvider<AiCallObserver> observerProvider
    ) {
        this(restClientBuilder, properties, observerProvider.getIfAvailable(NoopAiCallObserver::new));
    }

    public OpenAiCompatibleAiClient(
        RestClient.Builder restClientBuilder,
        OpenAiCompatibleProperties properties,
        AiCallObserver observer
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.observer = Objects.requireNonNull(observer, "observer must not be null.");
        this.restClient = restClientBuilder
            .baseUrl(this.properties.apiBaseUrlOrDefault())
            .build();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String apiKey = requireApiKey();
        String model = properties.chatModelOrDefault();

        ChatCompletionRequest request = new ChatCompletionRequest(
            model,
            List.of(
                new ChatMessage("system", requireText(systemPrompt, "systemPrompt")),
                new ChatMessage("user", requireText(userPrompt, "userPrompt"))
            ),
            properties.temperatureOrDefault()
        );

        long started = System.nanoTime();
        try {
            ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
            String content = firstContent(response);
            observer.record(AiCallObservation.success(
                PROVIDER,
                AiCallOperation.COMPLETION,
                model,
                COMPLETION_PROMPT_VERSION,
                elapsedSince(started),
                promptTokens(response),
                completionTokens(response),
                Instant.now()
            ));
            return content;
        } catch (RestClientResponseException exception) {
            recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, "RestClientResponseException");
            throw new IllegalStateException("AI completion request failed with provider response status "
                + exception.getStatusCode().value() + ".", exception);
        } catch (RestClientException exception) {
            recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, exception);
            throw new IllegalStateException("AI completion request failed.", exception);
        } catch (RuntimeException exception) {
            recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, exception);
            throw exception;
        }
    }

    @Override
    public EmbeddingVector embed(String input) {
        String normalizedInput = requireInput(input);
        String apiKey = requireApiKey();
        String model = properties.embeddingModelOrDefault();

        EmbeddingRequest request = new EmbeddingRequest(
            model,
            normalizedInput,
            "float",
            properties.embeddingDimensionsOrDefault()
        );

        long started = System.nanoTime();
        try {
            EmbeddingResponse response = restClient.post()
                .uri("/embeddings")
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(request)
                .retrieve()
                .body(EmbeddingResponse.class);
            EmbeddingVector vector = firstEmbedding(response);
            observer.record(AiCallObservation.success(
                PROVIDER,
                AiCallOperation.EMBEDDING,
                model,
                EMBEDDING_PROMPT_VERSION,
                elapsedSince(started),
                embeddingPromptTokens(response),
                null,
                Instant.now()
            ));
            return vector;
        } catch (RestClientResponseException exception) {
            recordFailure(AiCallOperation.EMBEDDING, model, EMBEDDING_PROMPT_VERSION, started, "RestClientResponseException");
            throw new IllegalStateException("AI embedding request failed with provider response status "
                + exception.getStatusCode().value() + ".", exception);
        } catch (RestClientException exception) {
            recordFailure(AiCallOperation.EMBEDDING, model, EMBEDDING_PROMPT_VERSION, started, exception);
            throw new IllegalStateException("AI embedding request failed.", exception);
        } catch (RuntimeException exception) {
            recordFailure(AiCallOperation.EMBEDDING, model, EMBEDDING_PROMPT_VERSION, started, exception);
            throw exception;
        }
    }

    private String requireApiKey() {
        String apiKey = properties.apiKeyOrBlank();
        if (apiKey.isBlank()) {
            throw new IllegalStateException("app.ai.openai-compatible.api-key must not be blank.");
        }
        return apiKey;
    }

    private static String firstContent(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("AI completion response did not include choices.");
        }

        ChatChoice choice = response.choices().getFirst();
        String content = choice == null || choice.message() == null ? null : choice.message().content();
        return requireText(content, "AI completion content");
    }

    private static EmbeddingVector firstEmbedding(EmbeddingResponse response) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("AI embedding response did not include data.");
        }

        EmbeddingItem item = response.data().getFirst();
        if (item == null || item.embedding() == null) {
            throw new IllegalStateException("AI embedding response did not include an embedding vector.");
        }
        return new EmbeddingVector(item.embedding());
    }

    private static String requireInput(String value) {
        String normalized = Objects.requireNonNull(value, "input must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("input must not be blank.");
        }
        return normalized;
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private void recordFailure(
        AiCallOperation operation,
        String model,
        String promptVersion,
        long started,
        RuntimeException exception
    ) {
        recordFailure(operation, model, promptVersion, started, exception.getClass().getSimpleName());
    }

    private void recordFailure(
        AiCallOperation operation,
        String model,
        String promptVersion,
        long started,
        String errorType
    ) {
        observer.record(AiCallObservation.failure(
            PROVIDER,
            operation,
            model,
            promptVersion,
            elapsedSince(started),
            errorType,
            Instant.now()
        ));
    }

    private static Duration elapsedSince(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static Integer promptTokens(ChatCompletionResponse response) {
        return response == null || response.usage() == null ? null : response.usage().prompt_tokens();
    }

    private static Integer completionTokens(ChatCompletionResponse response) {
        return response == null || response.usage() == null ? null : response.usage().completion_tokens();
    }

    private static Integer embeddingPromptTokens(EmbeddingResponse response) {
        return response == null || response.usage() == null ? null : response.usage().prompt_tokens();
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
        List<ChatChoice> choices,
        ChatUsage usage
    ) {
    }

    private record ChatUsage(
        Integer prompt_tokens,
        Integer completion_tokens
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record EmbeddingRequest(
        String model,
        String input,
        String encoding_format,
        Integer dimensions
    ) {
    }

    private record EmbeddingResponse(
        List<EmbeddingItem> data,
        EmbeddingUsage usage
    ) {
    }

    private record EmbeddingItem(
        List<Double> embedding
    ) {
    }

    private record EmbeddingUsage(
        Integer prompt_tokens,
        Integer total_tokens
    ) {
    }
}
