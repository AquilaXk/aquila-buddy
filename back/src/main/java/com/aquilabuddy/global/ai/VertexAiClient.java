package com.aquilabuddy.global.ai;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.application.port.out.ai.AiClient;
import com.aquilabuddy.application.port.out.ai.AiCompletionUseCase;
import com.aquilabuddy.domain.ai.AiCallObservation;
import com.aquilabuddy.domain.ai.AiCallOperation;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Vertex AI Gemini 응답을 애플리케이션의 AI completion port로 연결합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "vertex-ai")
public class VertexAiClient implements AiClient {

    private static final String PROVIDER = "vertex-ai";
    private static final String PROMPT_VERSION = "ai-client.complete.v1";

    private final VertexAiTextGenerator generator;
    private final AiCallObserver observer;
    private final Function<AiCompletionUseCase, String> modelResolver;

    public VertexAiClient(VertexAiTextGenerator generator) {
        this(generator, new NoopAiCallObserver(), "gemini-2.5-pro");
    }

    @Autowired
    public VertexAiClient(VertexAiTextGenerator generator, ObjectProvider<AiCallObserver> observerProvider, VertexAiProperties properties) {
        this(generator, observerProvider.getIfAvailable(NoopAiCallObserver::new), properties);
    }

    public VertexAiClient(VertexAiTextGenerator generator, AiCallObserver observer, String model) {
        this(generator, observer, ignored -> requireText(model, "model"));
    }

    public VertexAiClient(VertexAiTextGenerator generator, AiCallObserver observer, VertexAiProperties properties) {
        this(generator, observer, useCase -> Objects.requireNonNull(properties, "properties must not be null.")
            .completionModelOrDefault(useCase));
    }

    private VertexAiClient(VertexAiTextGenerator generator, AiCallObserver observer, Function<AiCompletionUseCase, String> modelResolver) {
        this.generator = Objects.requireNonNull(generator, "generator must not be null.");
        this.observer = Objects.requireNonNull(observer, "observer must not be null.");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver must not be null.");
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return complete(AiCompletionUseCase.GENERAL, systemPrompt, userPrompt);
    }

    @Override
    public String complete(AiCompletionUseCase useCase, String systemPrompt, String userPrompt) {
        String selectedModel = requireText(modelResolver.apply(normalizeUseCase(useCase)), "model");
        String prompt = buildPrompt(systemPrompt, userPrompt);
        long started = System.nanoTime();
        try {
            String content = requireText(generator.generate(selectedModel, prompt), "Vertex AI completion content");
            recordSuccess(selectedModel, started);
            return content;
        } catch (RuntimeException exception) {
            recordFailure(selectedModel, started, exception);
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
        String selectedModel = requireText(modelResolver.apply(normalizeUseCase(useCase)), "model");
        String prompt = buildPrompt(systemPrompt, userPrompt);
        long started = System.nanoTime();
        try {
            String content = requireText(
                generator.generateStream(selectedModel, prompt, onDelta),
                "Vertex AI completion content"
            );
            recordSuccess(selectedModel, started);
            return content;
        } catch (RuntimeException exception) {
            recordFailure(selectedModel, started, exception);
            throw exception;
        }
    }

    private String buildPrompt(String systemPrompt, String userPrompt) {
        return String.join("\n\n",
            "System prompt:",
            requireText(systemPrompt, "systemPrompt"),
            "User prompt:",
            requireText(userPrompt, "userPrompt")
        );
    }

    private void recordSuccess(String selectedModel, long started) {
        observer.record(AiCallObservation.success(
            PROVIDER,
            AiCallOperation.COMPLETION,
            selectedModel,
            PROMPT_VERSION,
            elapsedSince(started),
            null,
            null,
            Instant.now()
        ));
    }

    private void recordFailure(String selectedModel, long started, RuntimeException exception) {
        observer.record(AiCallObservation.failure(
            PROVIDER,
            AiCallOperation.COMPLETION,
            selectedModel,
            PROMPT_VERSION,
            elapsedSince(started),
            exception.getClass().getSimpleName(),
            failureReason(exception),
            Instant.now()
        ));
    }

    private static AiCompletionUseCase normalizeUseCase(AiCompletionUseCase useCase) {
        return useCase == null ? AiCompletionUseCase.GENERAL : useCase;
    }

    private static String failureReason(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getClass().getSimpleName() + ": " + message.strip();
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null.").trim();
        if (normalized.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank.");
        }
        return normalized;
    }

    private static Duration elapsedSince(long started) {
        return Duration.ofNanos(System.nanoTime() - started);
    }
}
