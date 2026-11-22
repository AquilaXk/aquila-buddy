package com.aquilabuddy.adapter.out.langfuse;

import com.aquilabuddy.application.port.out.ai.AiCallObserver;
import com.aquilabuddy.domain.ai.AiCallObservation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * AI 호출 관측 이벤트를 Langfuse ingestion API 호환 payload로 전송합니다.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai.langfuse", name = "enabled", havingValue = "true")
public class LangfuseAiTraceAdapter implements AiCallObserver {

    private static final String INGESTION_PATH = "/api/public/ingestion";

    private final RestClient restClient;
    private final LangfuseProperties properties;

    public LangfuseAiTraceAdapter(RestClient.Builder restClientBuilder, LangfuseProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null.");
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null.")
            .baseUrl(this.properties.baseUrlOrDefault())
            .build();
    }

    @Override
    public void record(AiCallObservation observation) {
        AiCallObservation normalized = Objects.requireNonNull(observation, "observation must not be null.");
        try {
            restClient.post()
                .uri(INGESTION_PATH)
                .headers(headers -> headers.setBasicAuth(
                    properties.publicKeyOrThrow(),
                    properties.secretKeyOrThrow(),
                    StandardCharsets.UTF_8
                ))
                .body(payload(normalized))
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException exception) {
            // 관측 전송 실패가 실제 AI 기능을 막지 않도록 삼킨다.
        }
    }

    private Map<String, Object> payload(AiCallObservation observation) {
        String eventId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", eventId);
        body.put("traceId", traceId);
        body.put("name", "ai." + observation.operation().name().toLowerCase());
        body.put("model", observation.model());
        body.put("startTime", observation.observedAt().minusMillis(observation.latencyMillis()).toString());
        body.put("endTime", observation.observedAt().toString());
        body.put("level", observation.success() ? "DEFAULT" : "ERROR");
        if (!observation.success()) {
            body.put("statusMessage", observation.errorType());
        }
        body.put("metadata", metadata(observation));
        body.put("usage", usage(observation));

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("type", "generation-create");
        event.put("timestamp", Instant.now().toString());
        event.put("body", body);

        return Map.of("batch", List.of(event));
    }

    private Map<String, Object> metadata(AiCallObservation observation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", observation.provider());
        metadata.put("operation", observation.operation().name());
        metadata.put("promptVersion", observation.promptVersion());
        metadata.put("latencyMillis", observation.latencyMillis());
        metadata.put("success", observation.success());
        metadata.put("environment", properties.environmentOrDefault());
        if (observation.errorType() != null) {
            metadata.put("errorType", observation.errorType());
        }
        if (observation.failureReason() != null) {
            metadata.put("failureReason", observation.failureReason());
        }
        return metadata;
    }

    private Map<String, Object> usage(AiCallObservation observation) {
        Map<String, Object> usage = new LinkedHashMap<>();
        if (observation.inputTokens() != null) {
            usage.put("input", observation.inputTokens());
        }
        if (observation.outputTokens() != null) {
            usage.put("output", observation.outputTokens());
        }
        return usage;
    }
}
