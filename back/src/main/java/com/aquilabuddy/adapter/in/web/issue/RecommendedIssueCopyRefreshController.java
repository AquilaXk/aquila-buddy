package com.aquilabuddy.adapter.in.web.issue;

import com.aquilabuddy.application.port.in.issue.RefreshRecommendedIssueCopyUseCase;
import com.aquilabuddy.domain.issue.RecommendedIssueCopyRefreshResult;
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
 * [Incoming Web Adapter] 운영자가 저장 추천 이슈 카드 copy를 dry-run으로 점검하고 갱신하는 API입니다.
 */
@RestController
@RequestMapping("/api/v1/issues")
public class RecommendedIssueCopyRefreshController {

    private final RefreshRecommendedIssueCopyUseCase refreshRecommendedIssueCopyUseCase;
    private final String operationToken;
    private final int maxLimit;

    public RecommendedIssueCopyRefreshController(
        RefreshRecommendedIssueCopyUseCase refreshRecommendedIssueCopyUseCase,
        @Value("${app.recommendation.copy-refresh.operation-token:}") String operationToken,
        @Value("${app.recommendation.copy-refresh.max-limit:200}") int maxLimit
    ) {
        this.refreshRecommendedIssueCopyUseCase = refreshRecommendedIssueCopyUseCase;
        this.operationToken = operationToken == null ? "" : operationToken.trim();
        this.maxLimit = maxLimit > 0 ? maxLimit : 200;
    }

    @PostMapping("/copy-refresh")
    ApiResponse<RecommendedIssueCopyRefreshResult> refreshCopy(
        @RequestHeader(value = "X-Aquila-Operation-Token", required = false) String requestedOperationToken,
        @RequestHeader(value = "X-Aquila-Operator", required = false) String operator,
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        requireOperationToken(requestedOperationToken, operator);
        return ApiResponse.success(
            refreshRecommendedIssueCopyUseCase.refreshRecommendedIssueCopy(normalizeLimit(limit), dryRun)
        );
    }

    private void requireOperationToken(String requestedOperationToken, String operator) {
        if (operationToken.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "recommended issue copy refresh operation token is not configured."
            );
        }
        String normalizedRequestedToken = normalize(requestedOperationToken);
        if (normalizedRequestedToken.isBlank() || !operationToken.equals(normalizedRequestedToken)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "recommended issue copy refresh operation token is invalid."
            );
        }
    }

    private int normalizeLimit(int requestedLimit) {
        if (requestedLimit < 1) {
            return 1;
        }
        return Math.min(requestedLimit, maxLimit);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
