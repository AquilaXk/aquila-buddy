package com.aquilabuddy.adapter.in.web.embedding;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.application.port.in.embedding.RecordEmbeddingBackfillAuditUseCase;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillAuditEvent;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import com.aquilabuddy.global.response.ApiResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * [Incoming Web Adapter] 운영용 embedding backfill API 요청을 처리합니다.
 */
@RestController
@RequestMapping("/api/v1/embeddings")
public class EmbeddingBackfillController {

    private final BackfillEmbeddingsUseCase backfillEmbeddingsUseCase;
    private final RecordEmbeddingBackfillAuditUseCase recordEmbeddingBackfillAuditUseCase;
    private final String operationToken;
    private final int maxLimit;
    private final Duration minInterval;
    private Instant lastAcceptedAt;

    public EmbeddingBackfillController(
        BackfillEmbeddingsUseCase backfillEmbeddingsUseCase,
        RecordEmbeddingBackfillAuditUseCase recordEmbeddingBackfillAuditUseCase,
        @Value("${app.embedding.backfill.operation-token:}") String operationToken,
        @Value("${app.embedding.backfill.max-limit:200}") int maxLimit,
        @Value("${app.embedding.backfill.min-interval-seconds:0}") long minIntervalSeconds
    ) {
        this.backfillEmbeddingsUseCase = backfillEmbeddingsUseCase;
        this.recordEmbeddingBackfillAuditUseCase = recordEmbeddingBackfillAuditUseCase;
        this.operationToken = operationToken == null ? "" : operationToken.trim();
        this.maxLimit = maxLimit > 0 ? maxLimit : 200;
        this.minInterval = Duration.ofSeconds(Math.max(0L, minIntervalSeconds));
    }

    /**
     * profile_embedding과 issue_embedding 누락분을 같은 limit으로 보강합니다.
     */
    @PostMapping("/backfill")
    ApiResponse<EmbeddingBackfillSummary> backfill(
        @RequestHeader(value = "X-Aquila-Operation-Token", required = false) String requestedOperationToken,
        @RequestHeader(value = "X-Aquila-Operator", required = false) String operator,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireOperationToken(requestedOperationToken, operator, limit);
        int effectiveLimit = normalizeLimit(limit);
        requireRateLimit(operator, limit, effectiveLimit);
        EmbeddingBackfillSummary summary;
        try {
            summary = backfillEmbeddingsUseCase.backfillEmbeddings(effectiveLimit);
        } catch (RuntimeException error) {
            recordEmbeddingBackfillAuditUseCase.record(EmbeddingBackfillAuditEvent.failed(
                operator,
                limit,
                effectiveLimit,
                error,
                Instant.now()
            ));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "embedding backfill failed.", error);
        }
        recordEmbeddingBackfillAuditUseCase.record(EmbeddingBackfillAuditEvent.succeeded(
            operator,
            limit,
            effectiveLimit,
            summary,
            Instant.now()
        ));
        return ApiResponse.success(summary);
    }

    private synchronized void requireRateLimit(String operator, int requestedLimit, int effectiveLimit) {
        if (minInterval.isZero() || minInterval.isNegative()) {
            return;
        }
        Instant now = Instant.now();
        if (lastAcceptedAt != null && now.isBefore(lastAcceptedAt.plus(minInterval))) {
            recordEmbeddingBackfillAuditUseCase.record(EmbeddingBackfillAuditEvent.rateLimited(
                operator,
                requestedLimit,
                effectiveLimit,
                now
            ));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "embedding backfill rate limit exceeded.");
        }
        lastAcceptedAt = now;
    }

    private void requireOperationToken(String requestedOperationToken, String operator, int requestedLimit) {
        if (operationToken.isBlank()) {
            recordDenied(operator, requestedLimit, "token-not-configured");
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "embedding backfill operation token is not configured."
            );
        }
        String normalizedRequestedToken = normalize(requestedOperationToken);
        if (normalizedRequestedToken.isBlank()) {
            recordDenied(operator, requestedLimit, "missing-token");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "embedding backfill operation token is invalid.");
        }
        if (!operationToken.equals(normalizedRequestedToken)) {
            recordDenied(operator, requestedLimit, "invalid-token");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "embedding backfill operation token is invalid.");
        }
    }

    private void recordDenied(String operator, int requestedLimit, String reason) {
        recordEmbeddingBackfillAuditUseCase.record(EmbeddingBackfillAuditEvent.denied(
            operator,
            requestedLimit,
            reason,
            Instant.now()
        ));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return 1;
        }
        return Math.min(requestedLimit, maxLimit);
    }
}
