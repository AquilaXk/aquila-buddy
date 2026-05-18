package com.aquilabuddy.adapter.in.web.embedding;

import com.aquilabuddy.application.port.in.embedding.BackfillEmbeddingsUseCase;
import com.aquilabuddy.domain.embedding.EmbeddingBackfillSummary;
import com.aquilabuddy.global.response.ApiResponse;
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
    private final String operationToken;
    private final int maxLimit;

    public EmbeddingBackfillController(
        BackfillEmbeddingsUseCase backfillEmbeddingsUseCase,
        @Value("${app.embedding.backfill.operation-token:}") String operationToken,
        @Value("${app.embedding.backfill.max-limit:200}") int maxLimit
    ) {
        this.backfillEmbeddingsUseCase = backfillEmbeddingsUseCase;
        this.operationToken = operationToken == null ? "" : operationToken.trim();
        this.maxLimit = maxLimit > 0 ? maxLimit : 200;
    }

    /**
     * profile_embedding과 issue_embedding 누락분을 같은 limit으로 보강합니다.
     */
    @PostMapping("/backfill")
    ApiResponse<EmbeddingBackfillSummary> backfill(
        @RequestHeader(value = "X-Aquila-Operation-Token", required = false) String requestedOperationToken,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireOperationToken(requestedOperationToken);
        return ApiResponse.success(backfillEmbeddingsUseCase.backfillEmbeddings(normalizeLimit(limit)));
    }

    private void requireOperationToken(String requestedOperationToken) {
        if (operationToken.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "embedding backfill operation token is not configured."
            );
        }
        if (!operationToken.equals(normalize(requestedOperationToken))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "embedding backfill operation token is invalid.");
        }
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
