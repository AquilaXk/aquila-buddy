package com.aquilabuddy.application.service.health;

import com.aquilabuddy.application.port.in.health.GetHealthStatusUseCase;
import com.aquilabuddy.domain.health.HealthStatus;
import org.springframework.stereotype.Service;

@Service
public class HealthStatusService implements GetHealthStatusUseCase {

    @Override
    public HealthStatus getHealthStatus() {
        return new HealthStatus("UP", "aquila-buddy");
    }
}
