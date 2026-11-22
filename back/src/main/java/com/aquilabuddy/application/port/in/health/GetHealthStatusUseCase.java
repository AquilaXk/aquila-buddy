package com.aquilabuddy.application.port.in.health;

import com.aquilabuddy.domain.health.HealthStatus;

/**
 * [Incoming Port] 서비스 상태를 조회하는 유스케이스 계약입니다.
 */
public interface GetHealthStatusUseCase {

    /**
     * 현재 애플리케이션 상태 정보를 반환합니다.
     */
    HealthStatus getHealthStatus();
}
