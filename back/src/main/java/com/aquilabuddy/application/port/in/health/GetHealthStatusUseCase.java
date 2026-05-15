package com.aquilabuddy.application.port.in.health;

import com.aquilabuddy.domain.health.HealthStatus;

public interface GetHealthStatusUseCase {

    HealthStatus getHealthStatus();
}
