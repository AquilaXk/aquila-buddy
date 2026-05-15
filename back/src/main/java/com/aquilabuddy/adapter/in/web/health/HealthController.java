package com.aquilabuddy.adapter.in.web.health;

import com.aquilabuddy.application.port.in.health.GetHealthStatusUseCase;
import com.aquilabuddy.domain.health.HealthStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final GetHealthStatusUseCase getHealthStatusUseCase;

    public HealthController(GetHealthStatusUseCase getHealthStatusUseCase) {
        this.getHealthStatusUseCase = getHealthStatusUseCase;
    }

    @GetMapping
    HealthStatus health() {
        return getHealthStatusUseCase.getHealthStatus();
    }
}
