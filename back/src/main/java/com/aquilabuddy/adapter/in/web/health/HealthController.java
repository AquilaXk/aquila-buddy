package com.aquilabuddy.adapter.in.web.health;

import com.aquilabuddy.application.port.in.health.GetHealthStatusUseCase;
import com.aquilabuddy.domain.health.HealthStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * [Incoming Web Adapter] 서비스 상태 확인 API 요청을 처리하는 REST controller입니다.
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final GetHealthStatusUseCase getHealthStatusUseCase;

    public HealthController(GetHealthStatusUseCase getHealthStatusUseCase) {
        this.getHealthStatusUseCase = getHealthStatusUseCase;
    }

    /**
     * 애플리케이션이 응답 가능한 상태인지 확인할 수 있는 상태 값을 반환합니다.
     */
    @GetMapping
    HealthStatus health() {
        return getHealthStatusUseCase.getHealthStatus();
    }
}
