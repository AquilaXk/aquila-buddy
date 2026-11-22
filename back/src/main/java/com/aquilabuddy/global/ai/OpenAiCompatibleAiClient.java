package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.application.port.out.ai.EmbeddingClient;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import com.aquilabuddy.domain.embedding.EmbeddingVector;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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
    private static final int FAILURE_REASON_MAX_LENGTH = 1000;
    private static final int MAX_SSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_SSE_LINES = 16_384;
    private static final int MAX_SSE_LINE_CHARS = 65_536;
    private static final int MAX_STREAM_OUTPUT_CHARS = 128_000;
    private static final int MAX_ERROR_BODY_BYTES = 4096;

    private final RestClient restClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleProperties properties;
    private final AiCallObserver observer;

    public OpenAiCompatibleAiClient(RestClient.Builder restClientBuilder, OpenAiCompatibleProperties properties) {
        this(restClientBuilder, properties, new NoopAiCallObserver());
    }

    @Autowired
    public OpenAiCompatibleAiClient(
        RestClient.Builder restClientBuilder,
        OpenAiCompatibleProperties properties,
        ObjectProvider<AiCallObserver> observerProvider,
        ObjectProvider<ObjectMapper> objectMapperProvider
    ) {
        this(
            restClientBuilder,
            properties,
            observerProvider.getIfAvailable(NoopAiCallObserver::new),
            objectMapperProvider.getIfAvailable(ObjectMapper::new)
        );
    }

    public OpenAiCompatibleAiClient(
        RestClient.Builder restClientBuilder,
        OpenAiCompatibleProperties properties,
        AiCallObserver observer
    ) {
        this(restClientBuilder, properties, observer, new ObjectMapper());
    }

    OpenAiCompatibleAiClient(
        RestClient.Builder restClientBuilder,
        OpenAiCompatibleProperties properties,
        AiCallObserver observer,
        ObjectMapper objectMapper
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.observer = Objects.requireNonNull(observer, "observer must not be null.");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null.");
        Duration timeout = Duration.ofMillis(this.properties.requestTimeoutMillisOrDefault());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .build();
        this.restClient = restClientBuilder
            .baseUrl(this.properties.apiBaseUrlOrDefault())
            .requestFactory(requestFactory(this.httpClient, timeout))
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
            properties.temperatureOrDefault(),
            null
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
            recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, exception);
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
    public String stream(
        AiCompletionUseCase useCase,
        String systemPrompt,
        String userPrompt,
        Consumer<String> onDelta
    ) {
        Objects.requireNonNull(onDelta, "onDelta must not be null.");
        String apiKey = requireApiKey();
        String model = properties.chatModelOrDefault();
        ChatCompletionRequest payload = new ChatCompletionRequest(
            model,
            List.of(
                new ChatMessage("system", requireText(systemPrompt, "systemPrompt")),
                new ChatMessage("user", requireText(userPrompt, "userPrompt"))
            ),
            properties.temperatureOrDefault(),
            Boolean.TRUE
        );

        long started = System.nanoTime();
        try {
            String requestBody = objectMapper.writeValueAsString(payload);
            Duration timeout = Duration.ofMillis(properties.requestTimeoutMillisOrDefault());
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(properties.apiBaseUrlOrDefault() + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorDetail = readBoundedError(response.body());
                String reason = hasText(errorDetail)
                    ? "Provider response status " + statusCode + ": " + errorDetail
                    : "Provider response status " + statusCode;
                recordFailure(
                    AiCallOperation.COMPLETION,
                    model,
                    COMPLETION_PROMPT_VERSION,
                    started,
                    "HttpStatusException",
                    truncateFailureReason(reason)
                );
                throw new IllegalStateException("AI completion request failed with provider response status " + statusCode + ".");
            }

            StreamAccumulator accumulator = readSseStream(response.body(), onDelta);
            String content = requireText(accumulator.content(), "AI completion content");
            observer.record(AiCallObservation.success(
                PROVIDER,
                AiCallOperation.COMPLETION,
                model,
                COMPLETION_PROMPT_VERSION,
                elapsedSince(started),
                accumulator.promptTokens(),
                accumulator.completionTokens(),
                Instant.now()
            ));
            return content;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            IllegalStateException wrapped = new IllegalStateException("AI streaming completion request interrupted.", exception);
            recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, wrapped);
            throw wrapped;
        } catch (IOException exception) {
            IllegalStateException wrapped = new IllegalStateException("AI completion request failed.", exception);
            recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, wrapped);
            throw wrapped;
        } catch (RuntimeException exception) {
            if (!(exception instanceof IllegalStateException && exception.getMessage() != null
                && exception.getMessage().contains("provider response status"))) {
                recordFailure(AiCallOperation.COMPLETION, model, COMPLETION_PROMPT_VERSION, started, exception);
            }
            throw exception;
        }
    }

    private StreamAccumulator readSseStream(InputStream rawStream, Consumer<String> onDelta) throws IOException {
        if (rawStream == null) {
            throw new IllegalStateException("AI completion stream body must not be null.");
        }
        StringBuilder fullContent = new StringBuilder();
        Integer promptTokens = null;
        Integer completionTokens = null;
        int lineCount = 0;

        try (InputStream bounded = new BoundedInputStream(rawStream, MAX_SSE_BYTES);
             BufferedReader reader = new BufferedReader(new InputStreamReader(bounded, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount > MAX_SSE_LINES) {
                    throw new IllegalStateException("AI SSE stream exceeded maximum line count.");
                }
                if (line.length() > MAX_SSE_LINE_CHARS) {
                    throw new IllegalStateException("AI SSE line exceeded maximum character limit.");
                }
                if (line.isBlank() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }

                ChatStreamChunk chunk = objectMapper.readValue(data, ChatStreamChunk.class);
                if (chunk.usage() != null) {
                    if (chunk.usage().prompt_tokens() != null) {
                        promptTokens = chunk.usage().prompt_tokens();
                    }
                    if (chunk.usage().completion_tokens() != null) {
                        completionTokens = chunk.usage().completion_tokens();
                    }
                }
                if (chunk.choices() == null || chunk.choices().isEmpty()) {
                    continue;
                }
                ChatStreamChoice choice = chunk.choices().getFirst();
                String delta = choice == null || choice.delta() == null ? null : choice.delta().content();
                if (delta != null && !delta.isEmpty()) {
                    if (fullContent.length() + delta.length() > MAX_STREAM_OUTPUT_CHARS) {
                        throw new IllegalStateException("AI SSE stream exceeded maximum content length.");
                    }
                    fullContent.append(delta);
                    onDelta.accept(delta);
                }
            }
        }
        return new StreamAccumulator(fullContent.toString(), promptTokens, completionTokens);
    }

    private static String readBoundedError(InputStream body) {
        if (body == null) {
            return "";
        }
        try (InputStream stream = body) {
            byte[] bytes = stream.readNBytes(MAX_ERROR_BODY_BYTES);
            return new String(bytes, StandardCharsets.UTF_8).strip();
        } catch (IOException ignored) {
            return "";
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
            EmbeddingVector vector = firstEmbedding(response, properties.embeddingDimensionsOrDefault());
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
            recordFailure(AiCallOperation.EMBEDDING, model, EMBEDDING_PROMPT_VERSION, started, exception);
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

    private static EmbeddingVector firstEmbedding(EmbeddingResponse response, int expectedDimensions) {
        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("AI embedding response did not include data.");
        }

        EmbeddingItem item = response.data().getFirst();
        if (item == null || item.embedding() == null) {
            throw new IllegalStateException("AI embedding response did not include an embedding vector.");
        }
        EmbeddingVector vector = new EmbeddingVector(item.embedding());
        if (vector.dimension() != expectedDimensions) {
            throw new IllegalStateException("AI embedding response dimension must be "
                + expectedDimensions + " but was " + vector.dimension() + ".");
        }
        return vector;
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
        recordFailure(operation, model, promptVersion, started, exception.getClass().getSimpleName(), failureReason(exception));
    }

    private void recordFailure(
        AiCallOperation operation,
        String model,
        String promptVersion,
        long started,
        RestClientResponseException exception
    ) {
        recordFailure(
            operation,
            model,
            promptVersion,
            started,
            "RestClientResponseException",
            failureReason(exception)
        );
    }

    private void recordFailure(
        AiCallOperation operation,
        String model,
        String promptVersion,
        long started,
        String errorType,
        String failureReason
    ) {
        observer.record(AiCallObservation.failure(
            PROVIDER,
            operation,
            model,
            promptVersion,
            elapsedSince(started),
            errorType,
            failureReason,
            Instant.now()
        ));
    }

    private static String failureReason(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        String detail = hasText(body)
            ? "Provider response status " + exception.getStatusCode().value() + ": " + body
            : "Provider response status " + exception.getStatusCode().value();
        return truncateFailureReason(detail);
    }

    private static String failureReason(RuntimeException exception) {
        String message = exceptionMessageChain(exception);
        String detail = hasText(message)
            ? exception.getClass().getSimpleName() + ": " + message
            : exception.getClass().getSimpleName();
        if (hasTimeoutSignal(exception)) {
            detail = hasText(message)
                ? exception.getClass().getSimpleName() + ": timeout - " + message
                : exception.getClass().getSimpleName() + ": timeout";
        }
        return truncateFailureReason(detail);
    }

    private static boolean hasTimeoutSignal(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String className = current.getClass().getSimpleName().toLowerCase();
            String message = current.getMessage();
            String lowerMessage = message == null ? "" : message.toLowerCase();
            if (className.contains("timeout") || lowerMessage.contains("timed out")
                || lowerMessage.contains("timeout") || lowerMessage.contains("request cancelled")) {
                return true;
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return false;
    }

    private static String exceptionMessageChain(Throwable exception) {
        StringBuilder message = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            if (hasText(current.getMessage())) {
                if (!message.isEmpty()) {
                    message.append(" | caused by: ");
                }
                message.append(current.getMessage().strip());
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return message.toString();
    }

    private static JdkClientHttpRequestFactory requestFactory(HttpClient httpClient, Duration timeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return requestFactory;
    }

    private static String truncateFailureReason(String reason) {
        if (!hasText(reason)) {
            return null;
        }
        String normalized = reason.strip();
        return normalized.length() <= FAILURE_REASON_MAX_LENGTH
            ? normalized
            : normalized.substring(0, FAILURE_REASON_MAX_LENGTH);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

    private static final class BoundedInputStream extends FilterInputStream {

        private final int maxBytes;
        private int bytesRead;

        private BoundedInputStream(InputStream in, int maxBytes) {
            super(in);
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value != -1 && ++bytesRead > maxBytes) {
                throw new IOException("AI SSE stream exceeded maximum byte limit.");
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = super.read(b, off, len);
            if (count > 0) {
                bytesRead += count;
                if (bytesRead > maxBytes) {
                    throw new IOException("AI SSE stream exceeded maximum byte limit.");
                }
            }
            return count;
        }
    }

    private record StreamAccumulator(
        String content,
        Integer promptTokens,
        Integer completionTokens
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(
        String model,
        List<ChatMessage> messages,
        double temperature,
        Boolean stream
    ) {
    }

    private record ChatMessage(
        String role,
        String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionResponse(
        List<ChatChoice> choices,
        ChatUsage usage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatUsage(
        Integer prompt_tokens,
        Integer completion_tokens
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatChoice(
        ChatMessageResponse message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatMessageResponse(
        String content
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatStreamChunk(
        List<ChatStreamChoice> choices,
        ChatUsage usage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatStreamChoice(
        ChatStreamDelta delta
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatStreamDelta(
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
