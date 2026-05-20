package com.aquilabuddy.adapter.in.web.ai;

import com.aquilabuddy.application.port.in.ai.GetAiCallObservabilityUseCase;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * [Incoming Web Adapter] AI provider 호출 관측 summary 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiCallObservabilityController {

    private final GetAiCallObservabilityUseCase getAiCallObservabilityUseCase;
    private final String operationToken;

    public AiCallObservabilityController(
        GetAiCallObservabilityUseCase getAiCallObservabilityUseCase,
        @Value("${app.ai.observability.operation-token:}") String operationToken
    ) {
        this.getAiCallObservabilityUseCase = getAiCallObservabilityUseCase;
        this.operationToken = operationToken == null ? "" : operationToken.trim();
    }

    @GetMapping("/observability")
    ApiResponse<AiCallObservabilityReport> observability(
        @RequestHeader(value = "X-Aquila-Operation-Token", required = false) String requestedOperationToken,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireOperationToken(requestedOperationToken);
        return ApiResponse.success(getAiCallObservabilityUseCase.getRecentAiCallObservability(limit));
    }

    private void requireOperationToken(String requestedOperationToken) {
        if (operationToken.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI observability operation token is not configured."
            );
        }
        String normalizedRequestedToken = requestedOperationToken == null ? "" : requestedOperationToken.trim();
        if (!operationToken.equals(normalizedRequestedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AI observability operation token is invalid.");
        }
    }
}
