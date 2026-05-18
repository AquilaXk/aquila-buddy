package com.aquilabuddy.adapter.in.web.ai;

import com.aquilabuddy.application.port.in.ai.GetAiCallObservabilityUseCase;
import com.aquilabuddy.domain.ai.AiCallObservabilityReport;
import com.aquilabuddy.global.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] AI provider 호출 관측 summary 조회 API를 제공합니다.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiCallObservabilityController {

    private final GetAiCallObservabilityUseCase getAiCallObservabilityUseCase;

    public AiCallObservabilityController(GetAiCallObservabilityUseCase getAiCallObservabilityUseCase) {
        this.getAiCallObservabilityUseCase = getAiCallObservabilityUseCase;
    }

    @GetMapping("/observability")
    ApiResponse<AiCallObservabilityReport> observability(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(getAiCallObservabilityUseCase.getRecentAiCallObservability(limit));
    }
}
