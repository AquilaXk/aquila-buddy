package com.aquilabuddy.application.service.health;

import com.aquilabuddy.application.port.in.health.GetHealthStatusUseCase;
import com.aquilabuddy.domain.health.HealthStatus;
import org.springframework.stereotype.Service;

/**
 * [Application Service] 서비스 상태 조회 유스케이스를 수행합니다.
 */
@Service
public class HealthStatusService implements GetHealthStatusUseCase {

    /**
     * 로컬 애플리케이션이 정상 기동된 상태임을 나타내는 상태 응답을 생성합니다.
     */
    @Override
    public HealthStatus getHealthStatus() {
        return new HealthStatus("UP", "aquila-buddy");
    }
}
